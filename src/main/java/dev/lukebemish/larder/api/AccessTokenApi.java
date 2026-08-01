package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequired;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

public record AccessTokenApi(
    @OpenApiRequired String name,
    @OpenApiRequired String key,
    @Nullable String token,
    @OpenApiRequired List<String> namespaces,
    @OpenApiRequired List<String> repositories,
    @OpenApiName("canpublish") @JsonProperty("canpublish") boolean canPublish,
    @OpenApiRequired LocalDateTime expires
) {
}
