package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record ApiSuccess(
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, String> details
) {
    public ApiSuccess() {
        this(null);
    }

    @Override
    public Map<String, String> details() {
        return details == null ? Map.of() : details;
    }
}
