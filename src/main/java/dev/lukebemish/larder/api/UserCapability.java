package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiName;

public enum UserCapability {
    @OpenApiName("dashboard") @JsonProperty("dashboard") DASHBOARD,
    @OpenApiName("admindashboard") @JsonProperty("admindashboard") ADMIN_DASHBOARD
}
