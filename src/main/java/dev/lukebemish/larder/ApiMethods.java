package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ListResponse;
import dev.lukebemish.larder.api.RepositoryBackendWithData;
import dev.lukebemish.larder.api.RepositoryUpdate;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.User;
import dev.lukebemish.larder.schema.UserNamespace;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class ApiMethods {
    private ApiMethods() {}

    static final UUID UUID_ISS = UUID.fromString("f26ee10c-dfd1-4aff-99f2-03140ad59e46");

    public static User newUser(ModelConnection connection, User user) throws SQLException {
        return connection.transact(c -> {
            var existing = c.find(Identifier.of(user));
            if (existing.isEmpty()) {
                c.insert(user);
            } else {
                c.update(user);
            }
            return c.select(Identifier.of(user));
        });
    }

    public static User whoAmI(Context context) {
        Larder.JwtIdentity identity = context.attribute(Larder.JWT_IDENTITY_KEY);
        return Objects.requireNonNull(identity).user();
    }

    public static Set<Permission> permissions(Context context) {
        Larder.JwtIdentity identity = context.attribute(Larder.JWT_IDENTITY_KEY);
        return Objects.requireNonNull(identity).permissions();
    }


    public static void listUsers(Context context) throws SQLException {
        context.json(
            new ListResponse<>(connection(context).select(User.REPRESENTATION))
        );
    }

    public static void listNamespaces(Context context) throws SQLException {
        var user = context.pathParam("user");
        UUID uuid;
        try {
            uuid = UUID.fromString(user);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a UUID: "+user);
        }
        var iam = whoAmI(context);
        if (!permissions(context).contains(Permission.ALLOW_ADMIN_DASHBOARD) && !iam.id().equals(uuid)) {
            throw new UnauthorizedResponse();
        }
        context.json(
            new ListResponse<>(connection(context).select(
                new UserNamespace.ByUser(Identifier.of(new User.Id(uuid)))
            ))
        );
    }

    private static ModelConnection connection(Context context) {
        return context.appData(Larder.CONNECTION_KEY);
    }

    public static void listRepositories(Context context) throws SQLException {
        if (!permissions(context).contains(Permission.ALLOW_DASHBOARD)) {
            throw new UnauthorizedResponse();
        }
        connection(context).transact(connection -> {
            var out = new ArrayList<RepositoryUpdate>();
            for (var repo : connection.select(Repository.REPRESENTATION)) {
                out.add(RepositoryUpdate.from(repo, connection));
            }
            context.json(new ListResponse<>(out));
        });
    }

    public static void getRepository(Context context) throws SQLException {
        if (!permissions(context).contains(Permission.ALLOW_DASHBOARD)) {
            throw new UnauthorizedResponse();
        }
        var name = context.pathParam("repositoryName");
        if (!isValidRepositoryName(name)) {
            throw new BadRequestResponse("Invalid repository name:: "+name);
        }
        var repo = connection(context).find(Identifier.of(new Repository.Id(name)));
        if (repo.isEmpty()) {
            throw new NotFoundResponse("Repository not found");
        }
        context.json(repo.get());
    }

    private static final Set<String> RESERVED_NAMES = Set.of("api", "dashboard", "publish", "_internal");
    private static final Pattern VALID_REPOSITORY_NAME = Pattern.compile("^[a-z0-9._-]+$");

    private static boolean isValidRepositoryName(String name) {
        if (RESERVED_NAMES.contains(name)) {
            return false;
        }
        return VALID_REPOSITORY_NAME.matcher(name).matches();
    }

    public static void listBackends(Context context) throws SQLException {
        if (!permissions(context).contains(Permission.ALLOW_ADMIN_DASHBOARD)) {
            throw new UnauthorizedResponse();
        }
        context.json(new ListResponse<>(
            connection(context).select(RepositoryBackend.REPRESENTATION)
        ));
    }

    public static void getBackend(Context context) throws SQLException {
        if (!permissions(context).contains(Permission.ALLOW_ADMIN_DASHBOARD)) {
            throw new UnauthorizedResponse();
        }
        var id = context.pathParam("id");
        UUID backendId;
        try {
            backendId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a UUID: "+id);
        }
        context.json(connection(context).transact(connection -> {
            var backend = connection.find(Identifier.of(new RepositoryBackend.Id(backendId)));

            if (backend.isEmpty()) {
                throw new NotFoundResponse("Backend not found");
            }

            return RepositoryBackendWithData.from(backend.get(), connection);
        }));
    }
}
