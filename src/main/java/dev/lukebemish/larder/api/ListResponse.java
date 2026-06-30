package dev.lukebemish.larder.api;

import java.util.List;

public record ListResponse<T>(List<T> values) {
}
