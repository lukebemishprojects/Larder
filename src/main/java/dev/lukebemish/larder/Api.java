package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.ApiSuccess;
import dev.lukebemish.larder.api.RepositoryBackendType;
import dev.lukebemish.larder.api.UserApi;
import dev.lukebemish.larder.api.UserCapability;
import dev.lukebemish.larder.api.UserNamespaceApi;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.BackendConfigurationType;
import dev.lukebemish.larder.schema.Deployment;
import dev.lukebemish.larder.schema.FilesystemBackendConfiguration;
import dev.lukebemish.larder.schema.Package;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryIndex;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
import dev.lukebemish.larder.schema.TokenRepository;
import dev.lukebemish.larder.schema.User;
import dev.lukebemish.larder.schema.UserNamespace;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class Api {
    private Api() {}

    static final UUID UUID_ISS = UUID.fromString("f26ee10c-dfd1-4aff-99f2-03140ad59e46");

    @OpenApi(
        path = "/dashboard/api/whoami",
        methods = HttpMethod.GET,
        summary = "Get the querying user",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = UserApi.class),
                description = "The user"
            ),
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void whoAmI(Context context) throws SQLException {
        var authUser = authenticatedUser(context);
        context.json(UserApi.from(connection(context).select(authUser)));
    }

    public static Identifier<User> authenticatedUser(Context context) {
        Larder.AuthInfo identity = context.attribute(Larder.AUTH_INFO_KEY);
        if (identity == null) {
            throw new UnauthorizedResponse();
        }
        return Objects.requireNonNull(Objects.requireNonNull(identity).user());
    }

    public static Set<? extends Role> authenticatedRoles(Context context) {
        Larder.AuthInfo identity = context.attribute(Larder.AUTH_INFO_KEY);
        if (identity == null) {
            return Set.of();
        }
        return Objects.requireNonNull(Objects.requireNonNull(identity).roles());
    }

    @OpenApi(
        path = "/dashboard/api/whatcanido",
        methods = HttpMethod.GET,
        summary = "Find the capabilities available to the current user",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = UserCapability[].class),
                description = "The user's capabilities"
            ),
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void whatCanIDo(Context ctx) {
        Larder.AuthInfo identity = ctx.attribute(Larder.AUTH_INFO_KEY);
        ctx.json(Objects.requireNonNull(identity).roles().stream()
            .map(role -> switch (role) {
                case Role.Builtin builtin -> switch (builtin) {
                    case ADMIN -> UserCapability.ADMIN_DASHBOARD;
                    case USER -> UserCapability.DASHBOARD;
                };
            })
            .collect(Collectors.toSet())
        );
    }

    @OpenApi(
        path = "/dashboard/admin/api/users",
        methods = HttpMethod.GET,
        summary = "List users",
        responses = @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = UserApi[].class),
            description = "Available users"
        ),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void listUsers(Context context) throws SQLException {
        context.json(
            connection(context).select(User.REPRESENTATION)
                .stream().map(UserApi::from).toList()
        );
    }

    @OpenApi(
        path = "/dashboard/api/namespaces/{user}/list",
        pathParams = @OpenApiParam(
            name = "user",
            type = UUID.class
        ),
        methods = HttpMethod.GET,
        summary = "List namespaces for user",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = UserNamespaceApi[].class),
                description = "Namespaces"
            ),
            @OpenApiResponse(status = "401", description = "No permission to inspect the provided user", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void listNamespaces(Context context) throws SQLException {
        var uuid = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        adminOrSelf(context, uuid);
        context.json(
            connection(context).select(
                new UserNamespace.ByUser(Identifier.of(User.REPRESENTATION, uuid))
            ).stream().map(UserNamespaceApi::from).toList()
        );
    }

    public static void adminOrSelf(Context context, UUID uuid) {
        if (!authenticatedRoles(context).contains(Role.Builtin.ADMIN) && !authenticatedUser(context).id().equals(uuid)) {
            throw new UnauthorizedResponse();
        }
    }

    public static void self(Context context, UUID uuid) {
        if (!authenticatedUser(context).id().equals(uuid)) {
            throw new UnauthorizedResponse();
        }
    }

    public static ModelConnection connection(Context context) {
        return context.appData(Larder.CONNECTION_KEY);
    }

    public static boolean canPublishToNamespace(ModelConnection connection, Identifier<User> user, String target) throws SQLException {
        for (var namespace : connection.select(new UserNamespace.ByUser(user))) {
            if (target.equals(namespace.value()) || target.startsWith(namespace.value() + ".")) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern VALID_NAMESPACE_NAME = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)*$");

    public static boolean isValidNamespace(String namespace) {
        return VALID_NAMESPACE_NAME.matcher(namespace).matches();
    }

    @OpenApi(
        path = "/dashboard/admin/api/repositories/{repositoryName}",
        pathParams = @OpenApiParam(
            name = "repositoryName"
        ),
        methods = HttpMethod.DELETE,
        summary = "Remove repository",
        responses = {
            @OpenApiResponse(
                status = "204",
                description = "Repository deleted"
            ),
            @OpenApiResponse(status = "404", description = "Repository not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void removeRepository(Context context) throws SQLException {
        var name = context.pathParam("repositoryName");
        if (!ApiRepositories.isValidRepositoryName(name)) {
            throw new BadRequestResponse("Invalid repository name: "+name);
        }
        connection(context).transact(connection -> {
            var found = connection.select(new Repository.ByName(name));
            if (found.isEmpty()) {
                throw new NotFoundResponse("Repository not found");
            }
            var id = Identifier.of(found.getFirst());
            var backend  = connection.select(found.getFirst().backend());
            switch (backend.type()) {
                case RepositoryBackendType.S3 -> connection.delete(new S3BackendConfiguration.ById(id, BackendConfigurationType.PRIMARY));
                case RepositoryBackendType.FILESYSTEM -> connection.delete(new FilesystemBackendConfiguration.ById(id, BackendConfigurationType.PRIMARY));
            }

            if (found.getFirst().deploymentBackend().isPresent()) {
                var deploymentBackend = connection.select(found.getFirst().deploymentBackend().get());
                switch (deploymentBackend.type()) {
                    case RepositoryBackendType.S3 -> connection.delete(new S3BackendConfiguration.ById(id, BackendConfigurationType.DEPLOYMENTS));
                    case RepositoryBackendType.FILESYSTEM -> connection.delete(new FilesystemBackendConfiguration.ById(id, BackendConfigurationType.DEPLOYMENTS));
                }
            }

            connection.delete(new TokenRepository.ByRepository(id)); // delete all associations of keys with this repository
            for (var deployment : connection.select(new Deployment.ByRepository(id))) {
                deployment.remove(connection);
            }
            connection.delete(id);
            context.status(HttpStatus.NO_CONTENT);
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/namespaces/{user}/create/{namespace}",
        pathParams = {@OpenApiParam(name = "user", type = UUID.class), @OpenApiParam(name = "namespace")},
        methods = HttpMethod.POST,
        summary = "Add user namespace",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "User namespace added",
                content = @OpenApiContent(from = ApiSuccess.class)
            ),
            @OpenApiResponse(status = "404", description = "User not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void addNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(User.REPRESENTATION, userUUID);
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var userNamespace = new UserNamespace(userId, namespace, true);
            var existing = c.select(new UserNamespace.ByUser(userId));
            if (existing.isEmpty()) {
                c.insert(userNamespace);
            } else {
                c.update(userNamespace);
            }
            context.json(new ApiSuccess());
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/namespaces/{user}/confirm/{namespace}",
        pathParams = {@OpenApiParam(name = "user", type = UUID.class), @OpenApiParam(name = "namespace")},
        methods = HttpMethod.POST,
        summary = "Confirm requested user namespace",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "User namespace confirmed",
                content = @OpenApiContent(from = ApiSuccess.class)
            ),
            @OpenApiResponse(status = "404", description = "User not found", content = @OpenApiContent(from = ApiError.class)),
            @OpenApiResponse(status = "404", description = "User namespace not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void confirmNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(User.REPRESENTATION, userUUID);
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var namespaceId = new UserNamespace.ByPair(userId, namespace);
            var existing = c.select(namespaceId);
            if (existing.isEmpty()) {
                throw new NotFoundResponse("User namespace not found");
            }
            c.update(existing.getFirst().withConfirmed());
            context.json(new ApiSuccess());
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/namespaces/{user}/delete/{namespace}",
        pathParams = {@OpenApiParam(name = "user", type = UUID.class), @OpenApiParam(name = "namespace")},
        methods = HttpMethod.POST,
        summary = "Remove user namespace",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "User namespace removed",
                content = @OpenApiContent(from = ApiSuccess.class)
            ),
            @OpenApiResponse(status = "404", description = "User not found", content = @OpenApiContent(from = ApiError.class)),
            @OpenApiResponse(status = "404", description = "User namespace not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void removeNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(User.REPRESENTATION, userUUID);
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var namespaceId = new UserNamespace.ByPair(userId, namespace);
            var existing = c.select(namespaceId);
            if (existing.isEmpty()) {
                throw new NotFoundResponse("User namespace not found");
            }
            c.delete(namespaceId);
            context.json(new ApiSuccess());
        });
    }

    @OpenApi(
        path = "/dashboard/api/namespaces/{user}/request/{namespace}",
        pathParams = {@OpenApiParam(name = "user", type = UUID.class), @OpenApiParam(name = "namespace")},
        methods = HttpMethod.POST,
        summary = "Request user namespace",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "User namespace confirmed",
                content = @OpenApiContent(from = ApiSuccess.class)
            ),
            @OpenApiResponse(status = "404", description = "User not found", content = @OpenApiContent(from = ApiError.class)),
            @OpenApiResponse(status = "401", description = "No permission to inspect the provided user", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void requestNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        adminOrSelf(context, userUUID);
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(User.REPRESENTATION, userUUID);
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var namespaceId = new UserNamespace.ByPair(userId, namespace);
            var existing = c.select(namespaceId);
            if (!existing.isEmpty()) {
                context.json(new ApiSuccess(Map.of("new", "false")));
                return;
            }
            c.insert(new UserNamespace(userId, namespace, false));
            context.json(new ApiSuccess(Map.of("new", "true")));
        });
    }
}
