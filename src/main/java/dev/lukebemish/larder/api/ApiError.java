package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record ApiError(
    String error,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, String> details
) {
    public ApiError(String error) {
        this(error, Map.of());
    }

    @Override
    public Map<String, String> details() {
        return details == null ? Map.of() : details;
    }
}
