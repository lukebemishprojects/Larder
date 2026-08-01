package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record AccessTokenRequest(
    @OpenApiRequired String name,
    @OpenApiRequired UUID user,
    @OpenApiRequired List<String> namespaces,
    @OpenApiRequired List<String> repositories,
    @OpenApiName("canpublish") @JsonProperty("canpublish") boolean canPublish,
    @OpenApiRequired Duration lifetime
) {
}
