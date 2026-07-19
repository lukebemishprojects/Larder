package dev.lukebemish.larder;

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
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.pebbletemplates.pebble.PebbleEngine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
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
    private final PebbleEngine templateEngine;

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

        var app = Javalin.create(config -> {
            // Database
            config.events.serverStopping(this.modelConnection::closeConnection);
            config.appData(CONNECTION_KEY, this.modelConnection);
            config.appData(APPLICATION_KEY, this);
            config.appData(TEMPLATE_ENGINE_KEY, this.templateEngine);

            config.validation.register(UUID.class, UUID::fromString);

            // General
            config.router.ignoreTrailingSlashes = true;

            // Error handling
            config.routes.exception(UnauthorizedResponse.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                var accept = ctx.header(Header.ACCEPT);
                if (accept == null || (accept.contains(ContentType.HTML) || accept.contains("*/*") || accept.isEmpty())) {
                    oidcAuthenticator.fillLoginRedirect(ctx);
                } else {
                    ctx.json(new ApiError(HttpStatus.UNAUTHORIZED.getMessage()));
                }
            });
            config.routes.exception(HttpResponseException.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                // TODO: non-JSON HTML-y response for other errors
                ctx.json(new ApiError(e.getMessage(), e.getDetails()));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
                // TODO: non-JSON HTML-y response for other errors
                LOGGER.error("Internal server error", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                ctx.json(new ApiError(
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
                pluginConfig.withDefinitionConfiguration((version, definition) -> {
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

                path("/dashboard", List.of(Role.Builtin.USER), () -> {
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
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/dashboard";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.directory = "/dev/lukebemish/larder/dashboard";
                staticFiles.roles = Set.of(Role.Builtin.USER);
            });
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/_internal";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.directory = "/dev/lukebemish/larder/indices/_internal";
                staticFiles.roles = Set.of();
            });
        }).start(port);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
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

        var dbHost = Objects.requireNonNull(System.getenv("LARDER_DB_HOST"));
        var dbPort = Objects.requireNonNull(System.getenv("LARDER_DB_PORT"));
        var dbName = Objects.requireNonNull(System.getenv("LARDER_DB_NAME"));
        var dbUser = Objects.requireNonNull(System.getenv("LARDER_DB_USER"));
        var dbPassword = Objects.requireNonNull(System.getenv("LARDER_DB_PASSWORD"));
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

        var larderOidcIssuer = Objects.requireNonNull(System.getenv("LARDER_OIDC_ISSUER"));
        var larderOidcClientId = Objects.requireNonNull(System.getenv("LARDER_OIDC_CLIENT_ID"));
        var larderOidcClientSecret = Objects.requireNonNull(System.getenv("LARDER_OIDC_CLIENT_SECRET"));

        var templateLoader = new ModuleLoader(Larder.class);
        templateLoader.setPrefix("/dev/lukebemish/larder/indices");
        PebbleEngine templateEngine = new PebbleEngine.Builder()
            .loader(templateLoader)
            .build();

        try {
            new Larder(
                isDev,
                new ModelConnection(DriverManager.getConnection(dbUrl, dbProps)),
                new OIDCAuthenticator(larderOidcIssuer, larderOidcClientId, larderOidcClientSecret, larderHost),
                larderPort,
                templateEngine
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
