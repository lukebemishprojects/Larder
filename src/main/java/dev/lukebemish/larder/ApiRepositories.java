package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.ApiSuccess;
import dev.lukebemish.larder.api.RepositoryApi;
import dev.lukebemish.larder.api.RepositoryBackendType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.BackendConfigurationType;
import dev.lukebemish.larder.schema.FilesystemBackendConfiguration;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static dev.lukebemish.larder.Api.connection;

class ApiRepositories {
    private static final Set<String> RESERVED_NAMES = Set.of("api", "dashboard", "publish", "_internal", "portal", "login", "logout", "signin", "swagger", "openapi");
    private static final Pattern VALID_REPOSITORY_NAME = Pattern.compile("^[a-z0-9._-]+$");

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
        path = "/dashboard/api/repositories",
        methods = HttpMethod.GET,
        summary = "List repository names",
        responses = @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = String[].class),
            description = "Available repositories"
        ),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void listRepositoryNames(Context context) throws SQLException {
        connection(context).transact(connection -> {
            var out = new ArrayList<String>();
            for (var repo : connection.select(Repository.REPRESENTATION)) {
                out.add(repo.name());
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

    public static boolean isValidRepositoryName(String name) {
        if (RESERVED_NAMES.contains(name)) {
            return false;
        }
        return VALID_REPOSITORY_NAME.matcher(name).matches();
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
        if (repository.supportsPublishPortal() && repository.deploymentBackend() == null) {
            throw new BadRequestResponse("Deployment backend must be provided to support publish portal");
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
                        case RepositoryBackendType.S3 -> c.delete(new S3BackendConfiguration.ById(repositoryId, BackendConfigurationType.PRIMARY));
                        case RepositoryBackendType.FILESYSTEM -> c.delete(new FilesystemBackendConfiguration.ById(repositoryId, BackendConfigurationType.PRIMARY));
                    }
                }
                if (existing.getFirst().deploymentBackend().isPresent() && !existing.getFirst().deploymentBackend().get().equals(deploymentBackendId)) {
                    var backendActual = c.select(existing.getFirst().deploymentBackend().get());
                    switch (backendActual.type()) {
                        case RepositoryBackendType.S3 -> c.delete(new S3BackendConfiguration.ById(repositoryId, BackendConfigurationType.DEPLOYMENTS));
                        case RepositoryBackendType.FILESYSTEM -> c.delete(new FilesystemBackendConfiguration.ById(repositoryId, BackendConfigurationType.DEPLOYMENTS));
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
                        BackendConfigurationType.PRIMARY,
                        backendId,
                        repository.s3Backend().bucket(),
                        repository.s3Backend().prefix()
                    );
                    var existingConfig = c.select(new S3BackendConfiguration.ById(repositoryId, BackendConfigurationType.PRIMARY));
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
                        BackendConfigurationType.PRIMARY,
                        backendId,
                        repository.filesystemBackend().prefix()
                    );
                    var existingConfig = c.select(new FilesystemBackendConfiguration.ById(repositoryId, BackendConfigurationType.PRIMARY));
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
                            BackendConfigurationType.DEPLOYMENTS,
                            deploymentBackendId,
                            repository.deploymentS3Backend().bucket(),
                            repository.deploymentS3Backend().prefix()
                        );
                        var existingConfig = c.select(new S3BackendConfiguration.ById(repositoryId, BackendConfigurationType.DEPLOYMENTS));
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
                            BackendConfigurationType.DEPLOYMENTS,
                            deploymentBackendId,
                            repository.deploymentFilesystemBackend().prefix()
                        );
                        var existingConfig = c.select(new FilesystemBackendConfiguration.ById(repositoryId, BackendConfigurationType.DEPLOYMENTS));
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
}
