package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.RepositoryBackendType;
import dev.lukebemish.larder.schema.S3Backend;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.UUID;

public record RepositoryBackendWithData(
    @Nullable UUID id,
    RepositoryBackendType type,
    @JsonProperty("s3backend") @Nullable S3BackendData s3Backend
) {
    public static RepositoryBackendWithData from(RepositoryBackend backend, ModelConnection connection) throws SQLException {
        S3BackendData s3BackendData = null;
        if (backend.type() == RepositoryBackendType.S3) {
            s3BackendData = S3BackendData.from(S3Backend.REPRESENTATION.select(connection, new S3Backend.Id(new Identifier<>(backend, RepositoryBackend.REPRESENTATION)).make(S3Backend.REPRESENTATION)));
        }
        return new RepositoryBackendWithData(
            backend.id(),
            backend.type(),
            s3BackendData
        );
    }
}
