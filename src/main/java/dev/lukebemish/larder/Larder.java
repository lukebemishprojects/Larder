package dev.lukebemish.larder;

import com.fasterxml.uuid.Generators;
import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.UserApi;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Schema;
import dev.lukebemish.larder.schema.User;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.config.Key;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
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
import java.util.stream.Stream;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

public class Larder {
    private static final Logger LOGGER = LoggerFactory.getLogger(Larder.class);
    public static final Key<ModelConnection> CONNECTION_KEY = new Key<>("orm model connection");
    public static final Key<Larder> APPLICATION_KEY = new Key<>("application context");

    private final boolean isDev;
    private final ModelConnection modelConnection;

    private Larder(boolean isDev, ModelConnection modelConnection) throws SQLException {
        this.isDev = isDev;
        this.modelConnection = modelConnection;

        start();
    }

    private void start() throws SQLException {
        this.modelConnection.migrate(Schema.MIGRATIONS, Schema.CURRENT_VERSION);

        var app = Javalin.create(config -> {
            // Database
            config.events.serverStopping(this.modelConnection::closeConnection);
            config.appData(CONNECTION_KEY, this.modelConnection);
            config.appData(APPLICATION_KEY, this);

            config.validation.register(UUID.class, UUID::fromString);

            // General
            config.router.ignoreTrailingSlashes = true;

            // Error handling
            config.routes.exception(UnauthorizedResponse.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                ctx.json(new ApiError(HttpStatus.UNAUTHORIZED.getMessage()));
            });
            config.routes.exception(HttpResponseException.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                ctx.json(new ApiError(e.getMessage(), e.getDetails()));
            });
            config.routes.exception(Exception.class, (e, ctx) -> {
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
                        get("whoami", ctx -> ctx.json(UserApi.from(ApiMethods.whoAmI(ctx))));
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
                staticFiles.directory = isDev ? "/dashboard" : "/dev/lukebemish/larder/dashboard";
                staticFiles.roles = Set.of(Role.Builtin.USER);
            });
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/_internal";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.directory = isDev ? "/indices/_internal" : "/dev/lukebemish/larder/indices/_internal";
                staticFiles.roles = Set.of();
            });
        }).start(8786);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    public record AuthInfo(@Nullable User user, Set<Role> roles) {}
    public static final String AUTH_INFO_KEY = "auth_info";

    static Set<Role> userRoles(Context context) throws SQLException {
        var app = context.appData(APPLICATION_KEY);
        if (context.attribute(AUTH_INFO_KEY) instanceof AuthInfo authInfo) {
            return authInfo.roles;
        }
        if (app.isDev) {
            var connection = context.appData(CONNECTION_KEY);
            var authInfo = new AuthInfo(
                ApiMethods.newUser(connection, new User(
                    "xyz@example.org",
                    // UUID is always kept the same so dev has a stable user
                    Generators.nameBasedGenerator(ApiMethods.UUID_ISS).generate("xyz@example.org")
                )),
                Set.of(Role.Builtin.ADMIN, Role.Builtin.USER)
            );
            context.attribute(AUTH_INFO_KEY, authInfo);
            return authInfo.roles;
        }
        // TODO: implement
        return Set.of();
    }

    private void authenticate(Context context) throws SQLException {
        var requiredRoles = context.routeRoles();
        var userRoles = userRoles(context);
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
        var signOutUrl = System.getenv("LARDER_SIGNOUT_URL");

        var isDev = appEnv.equals("dev");

        if (!isDev && Stream.of(signOutUrl).anyMatch(Objects::isNull)) {
            throw new RuntimeException("In production, JWT header validation must be set up for the dashboard and admin dashboard to determine identity!");
        }

        var dbHost = System.getenv("LARDER_DB_HOST");
        var dbPort = System.getenv("LARDER_DB_PORT");
        var dbName = System.getenv("LARDER_DB_NAME");
        var dbUser = System.getenv("LARDER_DB_USER");
        var dbPassword = System.getenv("LARDER_DB_PASSWORD");
        var dbUrl = String.format(
            "jdbc:postgresql://%s:%s/%s",
            URLEncoder.encode(dbHost, StandardCharsets.UTF_8),
            URLEncoder.encode(dbPort, StandardCharsets.UTF_8),
            URLEncoder.encode(dbName, StandardCharsets.UTF_8)
        );
        var dbProps = new Properties();
        dbProps.setProperty("user", dbUser);
        dbProps.setProperty("password", dbPassword);

        try {
            new Larder(
                isDev,
                new ModelConnection(DriverManager.getConnection(dbUrl, dbProps))
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
