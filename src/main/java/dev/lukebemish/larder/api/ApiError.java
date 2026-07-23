package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.javalin.openapi.JsonSchema;
import io.javalin.openapi.OpenApiRequired;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record ApiError(
    int code,
    String error,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, String> details
) {
    public ApiError(int code, String error) {
        this(code, error, Map.of());
    }

    @Override
    public Map<String, String> details() {
        return details == null ? Map.of() : details;
    }
}
