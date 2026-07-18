package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.ApiIdentifyingSuccess;
import dev.lukebemish.larder.api.ApiSuccess;
import dev.lukebemish.larder.api.RepositoryBackendApi;
import dev.lukebemish.larder.api.RepositoryApi;
import dev.lukebemish.larder.api.RepositoryBackendType;
import dev.lukebemish.larder.api.UserApi;
import dev.lukebemish.larder.api.UserCapability;
import dev.lukebemish.larder.api.UserNamespaceApi;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.RepositoryIndex;
import dev.lukebemish.larder.schema.S3Backend;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
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
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        }
    )
    public static User whoAmI(Context context) {
        Larder.AuthInfo identity = context.attribute(Larder.AUTH_INFO_KEY);
        return Objects.requireNonNull(Objects.requireNonNull(identity).user());
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
        }
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
            .filter(Objects::nonNull)
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
        )
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
        }
    )
    public static void listNamespaces(Context context) throws SQLException {
        var uuid = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        adminOrSelf(context, uuid);
        context.json(
            connection(context).select(
                new UserNamespace.ByUser(Identifier.of(new User.Id(uuid)))
            ).stream().map(UserNamespaceApi::from).toList()
        );
    }

    private static void adminOrSelf(Context context, UUID uuid) throws SQLException {
        if (!Larder.userRoles(context).contains(Role.Builtin.ADMIN) && !whoAmI(context).id().equals(uuid)) {
            throw new UnauthorizedResponse();
        }
    }

    private static ModelConnection connection(Context context) {
        return context.appData(Larder.CONNECTION_KEY);
    }

    @OpenApi(
        path = "/dashboard/admin/api/repositories",
        methods = HttpMethod.GET,
        summary = "List repositories",
        responses = @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = RepositoryApi[].class),
            description = "Available repositories"
        )
    )
    public static void listRepositories(Context context) throws SQLException {
        connection(context).transact(connection -> {
            var out = new ArrayList<RepositoryApi>();
            for (var repo : connection.select(Repository.REPRESENTATION)) {
                out.add(RepositoryApi.from(repo, connection));
            }
            context.json(out);
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/repositories/{repositoryName}",
        pathParams = @OpenApiParam(
            name = "repositoryName"
        ),
        methods = HttpMethod.GET,
        summary = "Get repository",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = RepositoryApi.class),
                description = "The repository"
            ),
            @OpenApiResponse(status = "404", description = "Repository not found", content = @OpenApiContent(from = ApiError.class))
        }
    )
    public static void getRepository(Context context) throws SQLException {
        var name = context.pathParam("repositoryName");
        if (!isValidRepositoryName(name)) {
            throw new BadRequestResponse("Invalid repository name: "+name);
        }
        connection(context).transact(c -> {
            var repo = c.find(Identifier.of(new Repository.Id(name)));
            if (repo.isEmpty()) {
                throw new NotFoundResponse("Repository not found");
            }
            context.json(RepositoryApi.from(repo.get(), c));
        });
    }

    private static final Set<String> RESERVED_NAMES = Set.of("api", "dashboard", "publish", "_internal");
    private static final Pattern VALID_REPOSITORY_NAME = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern VALID_NAMESPACE_NAME = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)*$");

    private static boolean isValidRepositoryName(String name) {
        if (RESERVED_NAMES.contains(name)) {
            return false;
        }
        return VALID_REPOSITORY_NAME.matcher(name).matches();
    }

    private static boolean isValidNamespace(String namespace) {
        return VALID_NAMESPACE_NAME.matcher(namespace).matches();
    }

    @OpenApi(
        path = "/dashboard/admin/api/backends",
        methods = HttpMethod.GET,
        summary = "List repository backends",
        responses = @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(
                from = RepositoryBackendApi[].class
            ),
            description = "Available backends"
        )
    )
    public static void listBackends(Context context) throws SQLException {
        connection(context).transact(c -> {
            context.json(
                c.select(RepositoryBackend.REPRESENTATION)
                    .stream().map(b -> {
                        try {
                            return RepositoryBackendApi.from(b, c);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList()
            );
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/backends/{id}",
        pathParams = @OpenApiParam(
            name = "id",
            type = UUID.class
        ),
        methods = HttpMethod.GET,
        summary = "Get backend and configuration data",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = RepositoryBackendApi.class),
                description = "The backend"
            ),
            @OpenApiResponse(status = "404", description = "Backend not found", content = @OpenApiContent(from = ApiError.class))
        }
    )
    public static void getBackend(Context context) throws SQLException {
        var backendId = context.pathParamAsClass("id", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        context.json(connection(context).transact(connection -> {
            var backend = connection.find(Identifier.of(new RepositoryBackend.Id(backendId)));

            if (backend.isEmpty()) {
                throw new NotFoundResponse("Backend not found");
            }

            return RepositoryBackendApi.from(backend.get(), connection);
        }));
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
        }
    )
    public static void removeRepository(Context context) throws SQLException {
        var name = context.pathParam("repositoryName");
        if (!isValidRepositoryName(name)) {
            throw new BadRequestResponse("Invalid repository name: "+name);
        }
        connection(context).transact(connection -> {
            var id = Identifier.of(new Repository.Id(name));
            var found = connection.find(id);
            if (found.isEmpty()) {
                throw new NotFoundResponse("Repository not found");
            }
            var backend  = connection.select(found.get().backend());
            switch (backend.type()) {
                case RepositoryBackendType.S3 -> {
                    connection.delete(Identifier.of(new S3BackendConfiguration.Id(id)));
                }
            }
            connection.delete(new RepositoryIndex.ByRepository(id));
            connection.delete(id);
            context.status(HttpStatus.NO_CONTENT);
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/backends/{id}",
        pathParams = @OpenApiParam(
            name = "id",
            type = UUID.class
        ),
        methods = HttpMethod.DELETE,
        summary = "Remove backend",
        responses = {
            @OpenApiResponse(
                status = "204",
                description = "Backend deleted"
            ),
            @OpenApiResponse(status = "404", description = "Backend not found", content = @OpenApiContent(from = ApiError.class))
        }
    )
    public static void removeBackend(Context context) throws SQLException {
        var backendUUID = context.pathParamAsClass("id", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        connection(context).transact(connection -> {
            var backendId = Identifier.of(new RepositoryBackend.Id(backendUUID));
            var inUse = connection.select(new Repository.ByBackend(backendId));
            if (!inUse.isEmpty()) {
                throw new BadRequestResponse("Cannot delete backend still in use by repositories");
            }
            var found = connection.find(backendId);
            if (found.isEmpty()) {
                throw new  NotFoundResponse("Backend not found");
            }
            connection.delete(Identifier.of(new S3Backend.Id(backendId)));
            connection.delete(backendId);
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
        }
    )
    public static void addNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(new User.Id(userUUID));
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var userNamespace = new UserNamespace(userId, namespace, true);
            var existing = c.find(Identifier.of(userNamespace));
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
        }
    )
    public static void confirmNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(new User.Id(userUUID));
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var namespaceId = Identifier.of(new UserNamespace.Id(userId, namespace));
            var existing = c.find(namespaceId);
            if (existing.isEmpty()) {
                throw new NotFoundResponse("User namespace not found");
            }
            c.update(existing.get().withConfirmed());
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
        }
    )
    public static void removeNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(new User.Id(userUUID));
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var namespaceId = Identifier.of(new UserNamespace.Id(userId, namespace));
            var existing = c.find(namespaceId);
            if (existing.isEmpty()) {
                throw new NotFoundResponse("User namespace not found");
            }
            c.delete(namespaceId);
            context.json(new ApiSuccess());
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/repositories/{repositoryName}",
        pathParams = @OpenApiParam(name = "repositoryName"),
        methods = HttpMethod.POST,
        summary = "Update repository",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "Repository updated",
                content = @OpenApiContent(from = ApiSuccess.class)
            ),
            @OpenApiResponse(status = "404", description = "Repository not found", content = @OpenApiContent(from = ApiError.class)),
            @OpenApiResponse(status = "404", description = "Repository backend not found", content = @OpenApiContent(from = ApiError.class)),
        },
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RepositoryApi.class), required = true)
    )
    public static void updateRepository(Context context) throws SQLException {
        var name = context.pathParam("repositoryName");
        if (!isValidRepositoryName(name)) {
            throw new BadRequestResponse("Invalid repository name: "+name);
        }
        var repository = context.bodyAsClass(RepositoryApi.class);
        if (!repository.name().equals(name)) {
            throw new BadRequestResponse("Repository name does not match");
        }
        if (repository.expirationDays() < 0) {
            throw new BadRequestResponse("Expiration days must be greater than 0");
        }
        connection(context).transact(c -> {
            var repositoryId = Identifier.of(new Repository.Id(name));
            var backendId = Identifier.of(new RepositoryBackend.Id(repository.backend()));
            var newBackend = c.find(backendId);
            if (newBackend.isEmpty()) {
                throw new NotFoundResponse("Backend not found");
            }
            var existing = c.find(repositoryId);
            var repositoryUpdated = new Repository(
                repository.name(),
                repository.supportsMavenDeploy(),
                repository.supportsPublishPortal(),
                repository.expirationDays(),
                repository.mutable(),
                backendId
            );
            if (existing.isEmpty()) {
                c.insert(repositoryUpdated);
            } else {
                c.update(repositoryUpdated);
                if (!existing.get().backend().equals(backendId)) {
                    var backendActual = c.select(existing.get().backend());
                    switch (backendActual.type()) {
                        case RepositoryBackendType.S3 -> {
                            c.delete(Identifier.of(new S3BackendConfiguration.Id(repositoryId)));
                        }
                    }
                }
            }
            switch (newBackend.get().type()) {
                case RepositoryBackendType.S3 -> {
                    if (repository.s3Backend() == null) {
                        throw new BadRequestResponse("No S3 backend configuration provided");
                    }
                    var config = new S3BackendConfiguration(
                        repositoryId,
                        Identifier.of(new S3Backend.Id(backendId)),
                        repository.s3Backend().bucket(),
                        repository.s3Backend().prefix()
                    );
                    var existingConfig = c.find(Identifier.of(config));
                    if (existingConfig.isEmpty()) {
                        c.insert(config);
                    } else {
                        c.update(config);
                    }
                }
            }
            context.json(new ApiSuccess());
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/backends/{id}",
        pathParams = @OpenApiParam(name = "id", type = UUID.class),
        methods = HttpMethod.POST,
        summary = "Update repository backend",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "Backend updated",
                content = @OpenApiContent(from = ApiSuccess.class)
            ),
            @OpenApiResponse(status = "404", description = "Existing repository backend configuration not found", content = @OpenApiContent(from = ApiError.class)),
        },
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RepositoryBackendApi.class), required = true)
    )
    public static void updateBackend(Context context) throws SQLException {
        var backendUUID = context.pathParamAsClass("id", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var backendId = Identifier.of(new RepositoryBackend.Id(backendUUID));
        connection(context).transact(c -> {
            var existing = c.find(backendId);
            if (existing.isEmpty()) {
                throw new NotFoundResponse("Backend not found");
            }
            var backend = context.bodyAsClass(RepositoryBackendApi.class);
            if (backend.id() != null && !backend.id().equals(backendUUID)) {
                throw new BadRequestResponse("Backend id does not match");
            }
            if (existing.get().type() != backend.type()) {
                throw new BadRequestResponse("Backend type cannot be changed");
            }
            switch (backend.type()) {
                case RepositoryBackendType.S3 -> {
                    if (backend.s3Backend() == null) {
                        throw new BadRequestResponse("No S3 backend configuration provided");
                    }
                    var existingConfig = c.find(Identifier.of(new S3Backend.Id(backendId)));
                    if (existingConfig.isEmpty()) {
                        throw new NotFoundResponse("S3 backend configuration not found");
                    }
                    var secretAccessKey = backend.s3Backend().secretAccessKey();
                    if (secretAccessKey == null) {
                        secretAccessKey = existingConfig.get().secretAccessKey();
                    }
                    var newS3Config = new S3Backend(
                        existingConfig.get().id(),
                        backend.s3Backend().region(),
                        backend.s3Backend().endpoint(),
                        backend.s3Backend().accessKeyId(),
                        secretAccessKey
                    );
                    c.update(newS3Config);
                }
            }
            context.json(new ApiSuccess());
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/backends",
        pathParams = @OpenApiParam(name = "id", type = UUID.class),
        methods = HttpMethod.POST,
        summary = "Create repository backend",
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "Backend created",
                content = @OpenApiContent(from = ApiIdentifyingSuccess.class)
            )
        },
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RepositoryBackendApi.class), required = true)
    )
    public static void createBackend(Context context) throws SQLException {
        connection(context).transact(c -> {
            var backend = context.bodyAsClass(RepositoryBackendApi.class);
            var backendId = UUID.randomUUID();
            var repoBackend = new RepositoryBackend(backendId, backend.type());
            c.insert(repoBackend);
            switch (backend.type()) {
                case RepositoryBackendType.S3 -> {
                    if (backend.s3Backend() == null) {
                        throw new BadRequestResponse("No S3 backend configuration provided");
                    }
                    var secretAccessKey = backend.s3Backend().secretAccessKey();
                    if (secretAccessKey == null) {
                        throw new BadRequestResponse("S3 backend must provide secret access key");
                    }
                    var newS3Config = new S3Backend(
                        Identifier.of(repoBackend),
                        backend.s3Backend().region(),
                        backend.s3Backend().endpoint(),
                        backend.s3Backend().accessKeyId(),
                        secretAccessKey
                    );
                    c.insert(newS3Config);
                }
            }
            context.json(new ApiIdentifyingSuccess(null, backendId));
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
        }
    )
    public static void requestNamespace(Context context) throws SQLException {
        var userUUID = context.pathParamAsClass("user", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        adminOrSelf(context, userUUID);
        var namespace = context.pathParam("namespace");
        if (!isValidNamespace(namespace)) {
            throw new BadRequestResponse("Invalid namespace: "+namespace);
        }
        var userId = Identifier.of(new User.Id(userUUID));
        connection(context).transact(c -> {
            var user = c.find(userId);
            if (user.isEmpty()) {
                throw new NotFoundResponse("User not found");
            }
            var namespaceId = Identifier.of(new UserNamespace.Id(userId, namespace));
            var existing = c.find(namespaceId);
            if (existing.isPresent()) {
                context.json(new ApiSuccess(Map.of("new", "false")));
                return;
            }
            c.insert(new UserNamespace(userId, namespace, false));
            context.json(new ApiSuccess(Map.of("new", "true")));
        });
    }
}
