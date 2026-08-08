package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.ApiSuccess;
import dev.lukebemish.larder.api.RepositoryApi;
import dev.lukebemish.larder.api.RepositoryBackendType;
import dev.lukebemish.larder.api.UserApi;
import dev.lukebemish.larder.api.UserCapability;
import dev.lukebemish.larder.api.UserNamespaceApi;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Deployment;
import dev.lukebemish.larder.schema.FilesystemBackendConfiguration;
import dev.lukebemish.larder.schema.Package;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
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
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    @OpenApi(
        path = "/dashboard/admin/api/repositories",
        methods = HttpMethod.GET,
        summary = "List repositories",
        responses = @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = RepositoryApi[].class),
            description = "Available repositories"
        ),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
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
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void getRepository(Context context) throws SQLException {
        var name = context.pathParam("repositoryName");
        if (!isValidRepositoryName(name)) {
            throw new BadRequestResponse("Invalid repository name: "+name);
        }
        connection(context).transact(c -> {
            var repo = c.select(new Repository.ByName(name));
            if (repo.isEmpty()) {
                throw new NotFoundResponse("Repository not found");
            }
            context.json(RepositoryApi.from(repo.getFirst(), c));
        });
    }

    private static final Set<String> RESERVED_NAMES = Set.of("api", "dashboard", "publish", "_internal", "portal", "login", "logout", "signin", "swagger", "openapi");
    private static final Pattern VALID_REPOSITORY_NAME = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern VALID_NAMESPACE_NAME = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)*$");

    public static boolean isValidRepositoryName(String name) {
        if (RESERVED_NAMES.contains(name)) {
            return false;
        }
        return VALID_REPOSITORY_NAME.matcher(name).matches();
    }

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
        if (!isValidRepositoryName(name)) {
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
                case RepositoryBackendType.S3 -> connection.delete(new S3BackendConfiguration.ById(id, Identifier.of(backend)));
                case RepositoryBackendType.FILESYSTEM -> connection.delete(new FilesystemBackendConfiguration.ById(id, Identifier.of(backend)));
            }
            connection.delete(new RepositoryIndex.ByRepository(id)); // delete all indices
            connection.delete(new TokenRepository.ByRepository(id)); // delete all associations of keys with this repository
            for (var deployment : connection.select(new Deployment.ByRepository(id))) {
                deployment.remove(connection);
            }
            connection.delete(new Package.ByRepository(id)); // delete indexed packages for this repository
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
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RepositoryApi.class), required = true),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
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
            var backendId = Identifier.of(RepositoryBackend.REPRESENTATION, repository.backend());
            var newBackend = c.find(backendId);
            if (newBackend.isEmpty()) {
                throw new NotFoundResponse("Backend not found");
            }

            var deploymentBackendId = repository.deploymentBackend() == null ? null : Identifier.of(RepositoryBackend.REPRESENTATION, repository.deploymentBackend());
            RepositoryBackend newDeploymentBackend = null;
            if (deploymentBackendId != null) {
                var found = c.find(deploymentBackendId);
                if (found.isEmpty()) {
                    throw new NotFoundResponse("Deployment backend not found");
                } else {
                    newDeploymentBackend = found.get();
                }
            }

            var existing = c.select(new Repository.ByName(name));
            Identifier<Repository> repositoryId = existing.isEmpty() ? Identifier.of(Repository.REPRESENTATION, UUID.randomUUID()) : Identifier.of(existing.getFirst());
            var repositoryUpdated = new Repository(
                repositoryId.id(),
                repository.name(),
                repository.supportsMavenDeploy(),
                repository.supportsPublishPortal(),
                Optional.ofNullable(deploymentBackendId),
                repository.expirationDays(),
                repository.mutable(),
                backendId,
                repository.supportsSnapshots()
            );
            if (existing.isEmpty()) {
                c.insert(repositoryUpdated);
            } else {
                c.update(repositoryUpdated);
                if (!existing.getFirst().backend().equals(backendId)) {
                    var backendActual = c.select(existing.getFirst().backend());
                    switch (backendActual.type()) {
                        case RepositoryBackendType.S3 -> c.delete(new S3BackendConfiguration.ById(repositoryId, Identifier.of(backendActual)));
                        case RepositoryBackendType.FILESYSTEM -> c.delete(new FilesystemBackendConfiguration.ById(repositoryId, Identifier.of(backendActual)));
                    }
                }
                if (existing.getFirst().deploymentBackend().isPresent() && !existing.getFirst().deploymentBackend().get().equals(deploymentBackendId)) {
                    var backendActual = c.select(existing.getFirst().deploymentBackend().get());
                    switch (backendActual.type()) {
                        case RepositoryBackendType.S3 -> c.delete(new S3BackendConfiguration.ById(repositoryId, Identifier.of(backendActual)));
                        case RepositoryBackendType.FILESYSTEM -> c.delete(new FilesystemBackendConfiguration.ById(repositoryId, Identifier.of(backendActual)));
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
                        backendId,
                        repository.s3Backend().bucket(),
                        repository.s3Backend().prefix()
                    );
                    var existingConfig = c.select(new S3BackendConfiguration.ById(repositoryId, backendId));
                    if (existingConfig.isEmpty()) {
                        c.insert(config);
                    } else {
                        c.update(config);
                    }
                }
                case RepositoryBackendType.FILESYSTEM -> {
                    if (repository.filesystemBackend() == null) {
                        throw new BadRequestResponse("No filesystem backend configuration provided");
                    }
                    var config = new FilesystemBackendConfiguration(
                        repositoryId,
                        backendId,
                        repository.filesystemBackend().prefix()
                    );
                    var existingConfig = c.select(new FilesystemBackendConfiguration.ById(repositoryId, backendId));
                    if (existingConfig.isEmpty()) {
                        c.insert(config);
                    } else {
                        c.update(config);
                    }
                }
            }

            if (newDeploymentBackend != null) {
                switch (newDeploymentBackend.type()) {
                    case RepositoryBackendType.S3 -> {
                        if (repository.deploymentS3Backend() == null) {
                            throw new BadRequestResponse("No deployment S3 backend configuration provided");
                        }
                        var config = new S3BackendConfiguration(
                            repositoryId,
                            deploymentBackendId,
                            repository.deploymentS3Backend().bucket(),
                            repository.deploymentS3Backend().prefix()
                        );
                        var existingConfig = c.select(new S3BackendConfiguration.ById(repositoryId, deploymentBackendId));
                        if (existingConfig.isEmpty()) {
                            c.insert(config);
                        } else {
                            c.update(config);
                        }
                    }
                    case RepositoryBackendType.FILESYSTEM -> {
                        if (repository.deploymentFilesystemBackend() == null) {
                            throw new BadRequestResponse("No deployment filesystem backend configuration provided");
                        }
                        var config = new FilesystemBackendConfiguration(
                            repositoryId,
                            deploymentBackendId,
                            repository.deploymentFilesystemBackend().prefix()
                        );
                        var existingConfig = c.select(new FilesystemBackendConfiguration.ById(repositoryId, deploymentBackendId));
                        if (existingConfig.isEmpty()) {
                            c.insert(config);
                        } else {
                            c.update(config);
                        }
                    }
                }
            }

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
