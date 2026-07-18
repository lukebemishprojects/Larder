package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.schema.S3Backend;
import io.javalin.openapi.OpenApiIgnore;
import io.javalin.openapi.OpenApiName;
import org.jspecify.annotations.Nullable;

public record S3BackendApi(
    String region,
    String endpoint,
    @JsonProperty("accesskeyid") @OpenApiName("accesskeyid") String accessKeyId,
    @JsonProperty("secretaccesskey") @OpenApiIgnore @Nullable String secretAccessKey
) {
    public static S3BackendApi from(S3Backend backend) {
        // Never expose the key here
        return new S3BackendApi(
            backend.region(),
            backend.endpoint(),
            backend.accessKeyId(),
            null
        );
    }
}
