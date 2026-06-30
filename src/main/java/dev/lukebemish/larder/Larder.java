package dev.lukebemish.larder;

import com.fasterxml.uuid.Generators;
import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.orm.Representation;
import dev.lukebemish.larder.schema.Schema;
import dev.lukebemish.larder.schema.User;
import io.javalin.Javalin;
import io.javalin.config.Key;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.plugin.ContextPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public class Larder {
    private static final Logger LOGGER = LoggerFactory.getLogger(Larder.class);
    private static final Key<ModelConnection> CONNECTION_KEY = new Key<>("orm model connection");

    private record AuthInfo(String aud, String jwtCertLocation, String jwtHeader, String signOutUrl) {}

    private final @Nullable AuthInfo authInfo;
    private final @Nullable AuthInfo adminAuthInfo;

    private final ModelConnection modelConnection;

    private Larder(@Nullable AuthInfo authInfo, @Nullable AuthInfo adminAuthInfo, ModelConnection modelConnection) throws SQLException {
        this.authInfo = authInfo;
        this.adminAuthInfo = adminAuthInfo;
        this.modelConnection = modelConnection;

        start();
    }

    private void start() throws SQLException {
        Representation.migrate(this.modelConnection, Schema.MIGRATIONS, Schema.CURRENT_VERSION);

        boolean isDev = authInfo == null;

        var app = Javalin.create(config -> {
            // Database
            config.events.serverStopping(this.modelConnection::closeConnection);
            config.appData(CONNECTION_KEY, this.modelConnection);

            // General
            config.router.ignoreTrailingSlashes = true;
            config.registerPlugin(new JwtPlugin());

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
                    isDev ? Map.of(
                        "message", e.getMessage(),
                        "location", e.getStackTrace()[0].toString()
                    ) : Map.of()
                ));
            });

            // Dashboard auth
            config.routes.before("dashboard/*", ctx -> {
                ctx.with(JwtPlugin.class).authenticate(ctx, authInfo);
            });
            config.routes.before("dashboard/admin/*", ctx -> {
                ctx.with(JwtPlugin.class).authenticate(ctx, adminAuthInfo);
            });
        }).start(8786);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
        }));
    }

    private final static class JwtPlugin extends ContextPlugin<JwtPlugin.Config, JwtPlugin.Extension> {
        static class Config {}
        static class Extension {
            private @Nullable JwtIdentity identity;

            void authenticate(Context context, @Nullable AuthInfo authInfo) throws SQLException {
                if (identity != null) {
                    return;
                }
                var ext = context.with(JwtPlugin.class);
                var connection = context.appData(CONNECTION_KEY);
                if (authInfo == null) {
                    identity = new JwtIdentity(
                        ApiMethods.newUser(connection, new User(
                            "xyz@example.org",
                            Generators.nameBasedGenerator(ApiMethods.UUID_ISS).generate("xyz@example.org")
                        )),
                        Set.of(Permission.ALLOW_DASHBOARD, Permission.ALLOW_ADMIN_DASHBOARD)
                    );
                } else {
                    throw new RuntimeException("Not yet implemented");
                }
            }
        }
        record JwtIdentity(User user, Set<Permission> permissions) {}

        @Override
        public Extension createExtension(Context context) {
            return new Extension();
        }
    }

    static void main(String[] args) {
        var appEnv = System.getenv("LARDER_ENV");
        if (appEnv == null) {
            appEnv = "prod";
        }
        var dashboardAud = System.getenv("LARDER_JWT_AUD");
        var adminDashboardAud = System.getenv("LARDER_ADMIN_JWT_AUD");
        var jwtCertLocation = System.getenv("LARDER_JWT_CERT_LOCATION");
        var jwtHeader = System.getenv("LARDER_JWT_HEADER");
        var signOutUrl = System.getenv("LARDER_SIGNOUT_URL");

        var isDev = appEnv.equals("dev");

        if (!isDev && Stream.of(
            dashboardAud, adminDashboardAud, jwtCertLocation, jwtHeader, signOutUrl
        ).anyMatch(Objects::isNull)) {
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
                isDev ? null : new AuthInfo(dashboardAud, jwtCertLocation, jwtHeader, signOutUrl),
                isDev ? null : new AuthInfo(adminDashboardAud, jwtCertLocation, jwtHeader, signOutUrl),
                new ModelConnection(DriverManager.getConnection(dbUrl, dbProps))
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
