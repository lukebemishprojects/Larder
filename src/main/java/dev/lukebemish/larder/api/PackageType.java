package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

public enum PackageType {
    @OpenApiName("ivy") @JsonProperty("ivy") IVY,
    @OpenApiName("maven") @JsonProperty("maven") MAVEN,
    @OpenApiName("gradle") @JsonProperty("gradle") GRADLE
}
