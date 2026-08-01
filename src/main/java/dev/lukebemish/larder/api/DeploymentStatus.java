package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.javalin.openapi.OpenApiRequired;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public record DeploymentStatus(
    @OpenApiRequired UUID deploymentId,
    @OpenApiRequired String deploymentName,
    @OpenApiRequired DeploymentState deploymentState,
    @OpenApiRequired List<URI> purls,
    @Nullable @JsonInclude(value = JsonInclude.Include.NON_EMPTY) List<String> errors
) {
}
