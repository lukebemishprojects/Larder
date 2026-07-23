package dev.lukebemish.larder;

import com.api.jsonata4java.expressions.Expressions;
import com.api.jsonata4java.expressions.ParseException;
import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Schema;
import dev.lukebemish.larder.schema.User;
import io.javalin.Javalin;
import io.javalin.config.Key;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson3;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.pebbletemplates.pebble.PebbleEngine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

public class Larder {
    private static final Logger LOGGER = LoggerFactory.getLogger(Larder.class);
    public static final Key<ModelConnection> CONNECTION_KEY = new Key<>("orm model connection");
    public static final Key<PebbleEngine> TEMPLATE_ENGINE_KEY = new Key<>("pebble template engine");
    public static final Key<Larder> APPLICATION_KEY = new Key<>("application context");

    private final boolean isDev;
    private final ModelConnection modelConnection;
    private final OIDCAuthenticator oidcAuthenticator;
    final PebbleEngine templateEngine;

    private final int port;

    private Larder(boolean isDev, ModelConnection modelConnection, OIDCAuthenticator oidcAuthenticator, int port, PebbleEngine templateEngine) throws SQLException {
        this.isDev = isDev;
        this.modelConnection = modelConnection;
        this.oidcAuthenticator = oidcAuthenticator;
        this.port = port;
        this.templateEngine = templateEngine;

        start();
    }

    private void start() throws SQLException {
        this.modelConnection.migrate(Schema.MIGRATIONS, Schema.CURRENT_VERSION);

        var indices = new Indices(this);

        var app = Javalin.create(config -> {
            // Database
            config.events.serverStopping(this.modelConnection::closeConnection);
            config.appData(CONNECTION_KEY, this.modelConnection);
            config.appData(APPLICATION_KEY, this);
            config.appData(TEMPLATE_ENGINE_KEY, this.templateEngine);

            var isNotLinked = isNotLinked();

            config.validation.register(UUID.class, UUID::fromString);

            // General
            config.router.ignoreTrailingSlashes = true;

            config.jsonMapper(new JavalinJackson3());

            // Error handling
            config.routes.exception(UnauthorizedResponse.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                if (isHtml(ctx.header(Header.ACCEPT))) {
                    var userRoles = oidcAuthenticator.userRoles(ctx);
                    if (userRoles.isEmpty()) {
                        oidcAuthenticator.fillLoginRedirect(ctx);
                        return;
                    }
                }
                specializeError(ctx, new ApiError(HttpStatus.UNAUTHORIZED.getCode(), HttpStatus.UNAUTHORIZED.getMessage()));
            });
            config.routes.exception(HttpResponseException.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                specializeError(ctx, new ApiError(e.getStatus(), e.getMessage(), e.getDetails()));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
                LOGGER.error("Internal server error", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                specializeError(ctx, new ApiError(
                    HttpStatus.INTERNAL_SERVER_ERROR.getCode(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getMessage(),
                    isDev ? (e.getMessage() != null ? Map.of(
                        "message", e.getMessage(),
                        "location", e.getStackTrace()[0].toString()
                    ) : Map.of(
                        "location", e.getStackTrace()[0].toString()
                    )) : Map.of()
                ));
            });

            config.registerPlugin(new OpenApiPlugin(pluginConfig -> {
                pluginConfig.withDefinitionConfiguration((_, definition) -> {
                    definition.info(info -> info.title("Larder API"));
                });
            }));
            config.registerPlugin(new SwaggerPlugin());
            config.registerPlugin(new ReDocPlugin());

            // Role auth
            config.routes.beforeMatched(this::authenticate);

            // TODO: Sign out

            // API methods
            config.routes.apiBuilder(() -> {
                // Redirected here after OIDC login
                get("/login", oidcAuthenticator::handleLoginRedirect);
                get("/logout", oidcAuthenticator::handleLogoutRequest);
                get("/signin", oidcAuthenticator::requestLogin, Role.Builtin.USER);

                path("/dashboard", List.of(Role.Builtin.USER), () -> {
                    get("logout", oidcAuthenticator::requestLogout);

                    path("admin", List.of(Role.Builtin.ADMIN), () -> {
                        path("api", () -> {
                            get("users", ApiMethods::listUsers);
                            get("repositories", ApiMethods::listRepositories);
                            get("repositories/{repositoryName}", ApiMethods::getRepository);
                            get("backends", ApiMethods::listBackends);
                            get("backends/{id}", ApiMethods::getBackend);

                            delete("repositories/{repositoryName}", ApiMethods::removeRepository);
                            delete("backends/{id}", ApiMethods::removeBackend);

                            post("namespaces/{user}/create/{namespace}", ApiMethods::addNamespace);
                            post("namespaces/{user}/confirm/{namespace}", ApiMethods::confirmNamespace);
                            post("namespaces/{user}/delete/{namespace}", ApiMethods::removeNamespace);

                            post("repositories/{repositoryName}", ApiMethods::updateRepository);
                            post("backends/{id}", ApiMethods::updateBackend);
                            post("backends", ApiMethods::createBackend);
                        });
                    });
                    path("api", () -> {
                        get("whoami", ApiMethods::whoAmI);
                        get("whatcanido", ApiMethods::whatCanIDo);
                        get("namespaces/{user}/list", ApiMethods::listNamespaces);

                        post("namespaces/{user}/request/{namespace}", ApiMethods::requestNamespace);
                    });
                });
            });

            // Static files (dev + prod prefixes for indices styling and dashboard)
            // First, we unpack these files to temporary directories

            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/dashboard";
                staticFiles.location = isNotLinked ? Location.CLASSPATH : Location.EXTERNAL;
                staticFiles.directory = unpackIfNeeded(Larder.class.getModule(), "/dev/lukebemish/larder/dashboard", isNotLinked);
                staticFiles.roles = Set.of(Role.Builtin.USER);
            });
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/_internal";
                staticFiles.location = isNotLinked ? Location.CLASSPATH : Location.EXTERNAL;
                staticFiles.directory = unpackIfNeeded(Larder.class.getModule(), "/dev/lukebemish/larder/indices/_internal", isNotLinked);
                staticFiles.roles = Set.of();
            });

            // Use of spa handler lets this run after static files
            config.spaRoot.addHandler("/", ctx -> {
                var path = normalizePath(ctx.path());
                if (path.equals("/")) {
                    indices.listRepositories(ctx);
                    return;
                }
                var firstSlash = path.indexOf('/', 1);
                var repositoryName = firstSlash == -1 ? path.substring(1) : path.substring(1,  firstSlash);
                var rest = firstSlash == -1 ? "/" : path.substring(firstSlash);
                indices.listAt(ctx, rest, repositoryName, firstSlash == -1);
            });
        }).start(port);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    private String normalizePath(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static void specializeError(Context ctx, ApiError apiError) {
        var accept = ctx.header(Header.ACCEPT);
        if (isHtml(accept)) {
            var template = ctx.appData(TEMPLATE_ENGINE_KEY).getTemplate("error-page.html");
            var writer = new StringWriter();
            try {
                template.evaluate(writer, Map.of(
                    "error", apiError
                ), Locale.ROOT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ctx.html(writer.toString());
        } else {
            ctx.json(apiError);
        }
    }

    private static boolean isHtml(@Nullable String accept) {
        return accept == null || (accept.contains(ContentType.HTML) || accept.contains("*/*") || accept.isEmpty());
    }

    private static boolean isNotLinked() {
        var url = Larder.class.getResource("/"+Larder.class.getName().replace('.', '/')+".class");
        return url != null && Objects.equals(url.getProtocol(), "jar");
    }

    private static String unpackIfNeeded(Module module, String path, boolean isInJar) {
        if (isInJar) {
            return path;
        }
        else {
            var trimmedPath = path.startsWith("/") ? path.substring(1) : path;
            try (var moduleReader = module.getLayer().configuration().modules().stream()
                .filter(it -> it.name().equals(module.getName()))
                .findAny()
                .orElseThrow()
                .reference()
                .open()) {
                var tempDir = Files.createTempDirectory("larder");
                for (var resourcePath : moduleReader.list()
                    .filter(it -> it.startsWith(trimmedPath))
                    .toList()) {
                    var relPath = resourcePath.substring(trimmedPath.length());
                    if (relPath.startsWith("/")) {
                        relPath = relPath.substring(1);
                    }
                    try (var rStream = module.getResourceAsStream("/"+resourcePath)) {
                        var target = tempDir.resolve(relPath);
                        Files.createDirectories(target.getParent());
                        Files.copy(rStream, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                return tempDir.toAbsolutePath().toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public record AuthInfo(User.@Nullable Id user, Set<Role> roles) {}
    public static final String AUTH_INFO_KEY = "auth_info";

    private void authenticate(Context context) {
        var requiredRoles = context.routeRoles();
        var userRoles = oidcAuthenticator.userRoles(context);
        if (userRoles.containsAll(requiredRoles)) {
            return; // User has all required roles to access
        }
        throw new UnauthorizedResponse();
    }

    static void main(String[] args) {
        var appEnv = System.getenv("LARDER_ENV");
        if (appEnv == null) {
            appEnv = "prod";
        }

        var isDev = appEnv.equals("dev");

        var dbHost = Objects.requireNonNull(System.getenv("LARDER_DB_HOST"), "ENV[LARDER_DB_HOST]");
        var dbPort = Objects.requireNonNull(System.getenv("LARDER_DB_PORT"), "ENV[LARDER_DB_PORT]");
        var dbName = Objects.requireNonNull(System.getenv("LARDER_DB_NAME"), "ENV[LARDER_DB_NAME]");
        var dbUser = Objects.requireNonNull(System.getenv("LARDER_DB_USER"), "ENV[LARDER_DB_USER]");
        var dbPassword = Objects.requireNonNull(System.getenv("LARDER_DB_PASSWORD"), "ENV[LARDER_DB_PASSWORD]");
        var dbUrl = String.format(
            "jdbc:postgresql://%s:%s/%s",
            URLEncoder.encode(dbHost, StandardCharsets.UTF_8),
            URLEncoder.encode(dbPort, StandardCharsets.UTF_8),
            URLEncoder.encode(dbName, StandardCharsets.UTF_8)
        );
        var dbProps = new Properties();
        dbProps.setProperty("user", dbUser);
        dbProps.setProperty("password", dbPassword);

        var larderPort = Integer.parseInt(System.getenv().getOrDefault("LARDER_PORT", "8786"));
        var larderHost = System.getenv().getOrDefault("LARDER_HOST", "http://localhost:"+larderPort);

        var larderOidcIssuer = Objects.requireNonNull(System.getenv("LARDER_OIDC_ISSUER"), "ENV[LARDER_OIDC_ISSUER]");
        var larderOidcClientId = Objects.requireNonNull(System.getenv("LARDER_OIDC_CLIENT_ID"), "ENV[LARDER_OIDC_CLIENT_ID]");
        var larderOidcClientSecret = Objects.requireNonNull(System.getenv("LARDER_OIDC_CLIENT_SECRET"), "ENV[LARDER_OIDC_CLIENT_SECRET]");

        var larderOidcAdditionalScopes = Arrays.stream(System.getenv().getOrDefault("LARDER_OIDC_ADDITIONAL_SCOPES", "").split(" "))
            .filter(s -> !s.isEmpty())
            .toList();

        var larderOidcRoleAdminExpression = System.getenv().getOrDefault("LARDER_OIDC_ROLE_ADMIN", "false");
        Expressions larderOidcRoleAdminExpressionCompiled;
        try {
            larderOidcRoleAdminExpressionCompiled = Expressions.parse(larderOidcRoleAdminExpression);
        } catch (ParseException | IOException e) {
            throw new RuntimeException(e);
        }

        var templateLoader = new ModuleLoader(Larder.class);
        templateLoader.setPrefix("/dev/lukebemish/larder/indices");
        PebbleEngine templateEngine = new PebbleEngine.Builder()
            .loader(templateLoader)
            .build();

        try {
            new Larder(
                isDev,
                new ModelConnection(DriverManager.getConnection(dbUrl, dbProps)),
                new OIDCAuthenticator(larderOidcIssuer, larderOidcClientId, larderOidcClientSecret, larderHost, larderOidcAdditionalScopes, larderOidcRoleAdminExpressionCompiled),
                larderPort,
                templateEngine
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
