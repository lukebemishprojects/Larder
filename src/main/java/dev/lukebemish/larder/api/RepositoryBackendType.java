package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

public enum RepositoryBackendType {
    @OpenApiName("s3backend") @JsonProperty("s3backend") S3
}
