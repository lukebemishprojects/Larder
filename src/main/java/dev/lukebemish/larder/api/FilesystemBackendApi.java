package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.schema.FilesystemBackend;
import dev.lukebemish.larder.schema.S3Backend;
import io.javalin.openapi.Nullability;
import io.javalin.openapi.OpenApiIgnore;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiPropertyType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record FilesystemBackendApi(
    @OpenApiPropertyType(definedBy = String.class, nullability = Nullability.NULLABLE) @JsonInclude(JsonInclude.Include.NON_ABSENT) Optional<Location> location
) {
    public static FilesystemBackendApi from(FilesystemBackend backend) {
        // Never expose the key here
        return new FilesystemBackendApi(
            backend.location()
        );
    }
}
