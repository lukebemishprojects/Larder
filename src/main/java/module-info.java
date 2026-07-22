module dev.lukebemish.larder {
    requires java.sql;
    requires io.javalin;

    // UUID 5
    requires com.fasterxml.uuid;

    // Annotations, only needed at compile time
    requires static org.jspecify;
    requires static dev.lukebemish.polymorphicsignatures;

    // Javalin openapi and docs
    requires javalin.openapi.plugin;
    requires javalin.swagger.plugin;
    requires javalin.redoc.plugin;

    // Template processing
    requires io.pebbletemplates;

    // OAuth2, OIDC, and JWTs
    requires scribejava.core;
    requires scribejava.apis;
    requires jjwt.api;

    // JSON Serialization
    requires tools.jackson.databind;

    // JSON Expression Parsing (for OIDC-based role expressions)
    requires JSONata4Java;

    opens dev.lukebemish.larder.api to tools.jackson.databind;
}
