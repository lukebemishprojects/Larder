package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.S3Backend;
import io.javalin.openapi.OpenApiName;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.UUID;

public record RepositoryBackendApi(
    @Nullable UUID id,
    RepositoryBackendType type,
    @JsonProperty("s3backend") @OpenApiName("s3backend") @Nullable S3BackendApi s3Backend
) {
    public static RepositoryBackendApi from(RepositoryBackend backend, ModelConnection connection) throws SQLException {
        S3BackendApi s3BackendData = null;
        if (backend.type() == RepositoryBackendType.S3) {
            s3BackendData = S3BackendApi.from(connection.select(Identifier.of(new S3Backend.Id(Identifier.of(backend)))));
        }
        return new RepositoryBackendApi(
            backend.id(),
            backend.type(),
            s3BackendData
        );
    }
}
