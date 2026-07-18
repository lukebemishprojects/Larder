package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record ApiIdentifyingSuccess(
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, String> details,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable UUID id
) {
    @Override
    public Map<String, String> details() {
        return details == null ? Map.of() : details;
    }
}
