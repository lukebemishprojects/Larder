package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record ApiError(
    String error,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> details
) {
    public ApiError(String error) {
        this(error, Map.of());
    }
}
