package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.schema.S3Backend;
import org.jspecify.annotations.Nullable;

public record S3BackendData(
    String region,
    String endpoint,
    @JsonProperty("accesskeyid") String accessKeyId,
    @JsonProperty("secretaccesskey") @Nullable String secretAccessKey
) {
    public static S3BackendData from(S3Backend backend) {
        return new S3BackendData(
            backend.region(),
            backend.endpoint(),
            backend.accessKeyId(),
            backend.secretAccessKey()
        );
    }
}
