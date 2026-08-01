package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.ApiIdentifyingSuccess;
import dev.lukebemish.larder.api.ApiSuccess;
import dev.lukebemish.larder.api.Location;
import dev.lukebemish.larder.api.RepositoryBackendApi;
import dev.lukebemish.larder.api.RepositoryBackendType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.FilesystemBackend;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.S3Backend;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

final class ApiBackends {
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
        ),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void listBackends(Context context) throws SQLException {
        Api.connection(context).transact(c -> {
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
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void getBackend(Context context) throws SQLException {
        var backendId = context.pathParamAsClass("id", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        context.json(Api.connection(context).transact(connection -> {
            var backend = connection.find(Identifier.of(RepositoryBackend.REPRESENTATION, backendId));

            if (backend.isEmpty()) {
                throw new NotFoundResponse("Backend not found");
            }

            return RepositoryBackendApi.from(backend.get(), connection);
        }));
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
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void removeBackend(Context context) throws SQLException {
        var backendUUID = context.pathParamAsClass("id", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        Api.connection(context).transact(connection -> {
            var backendId = Identifier.of(RepositoryBackend.REPRESENTATION, backendUUID);
            var inUse = connection.select(new Repository.ByBackend(backendId));
            if (!inUse.isEmpty()) {
                throw new BadRequestResponse("Cannot delete backend still in use by repositories");
            }
            var found = connection.find(backendId);
            if (found.isEmpty()) {
                throw new  NotFoundResponse("Backend not found");
            }
            connection.delete(backendId);
            context.status(HttpStatus.NO_CONTENT);
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
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RepositoryBackendApi.class), required = true),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void updateBackend(Context context) throws SQLException {
        var backendUUID = context.pathParamAsClass("id", UUID.class)
            .getOrThrow(m -> new BadRequestResponse("Not a UUID: "+m.get("value")));
        var backendId = Identifier.of(RepositoryBackend.REPRESENTATION, backendUUID);
        Api.connection(context).transact(c -> {
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
                    var existingConfig = c.select(new S3Backend.ById(backendId));
                    if (existingConfig.isEmpty()) {
                        throw new NotFoundResponse("S3 backend configuration not found");
                    }
                    var secretAccessKey = backend.s3Backend().secretAccessKey();
                    if (secretAccessKey == null) {
                        secretAccessKey = existingConfig.getFirst().secretAccessKey();
                    }
                    var newS3Config = new S3Backend(
                        existingConfig.getFirst().id(),
                        backend.s3Backend().region(),
                        backend.s3Backend().endpoint(),
                        backend.s3Backend().accessKeyId(),
                        secretAccessKey
                    );
                    c.update(newS3Config);
                }
                case RepositoryBackendType.FILESYSTEM -> {
                    if (backend.filesystemBackend() == null) {
                        throw new BadRequestResponse("No filesystem backend configuration provided");
                    }
                    var existingConfig = c.select(new FilesystemBackend.ById(backendId));
                    if (existingConfig.isEmpty()) {
                        throw new NotFoundResponse("Filesystem backend configuration not found");
                    }
                    var newFilesystemConfig = new FilesystemBackend(
                        existingConfig.getFirst().id(),
                        backend.filesystemBackend().location()
                    );
                    c.update(newFilesystemConfig);
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
        requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RepositoryBackendApi.class), required = true),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void createBackend(Context context) throws SQLException {
        Api.connection(context).transact(c -> {
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
                case RepositoryBackendType.FILESYSTEM -> {
                    if (backend.filesystemBackend() == null) {
                        throw new BadRequestResponse("No filesystem backend configuration provided");
                    }
                    var newFilesystemConfig = new FilesystemBackend(
                        Identifier.of(repoBackend),
                        backend.filesystemBackend().location()
                    );
                    c.insert(newFilesystemConfig);
                }
            }
            context.json(new ApiIdentifyingSuccess(null, backendId));
        });
    }

    @OpenApi(
        path = "/dashboard/admin/api/backends/filesystem",
        methods = HttpMethod.GET,
        summary = "List available filesystem backend locations",
        responses = @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(
                from = String[].class
            ),
            description = "Available filesystem backend locations"
        ),
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    public static void listFilesystemLocations(Context context) {
        context.json(Arrays.stream(Location.values()).toList());
    }
}
