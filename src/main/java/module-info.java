module dev.lukebemish.larder {
    requires java.sql;
    requires io.javalin;
    requires com.fasterxml.jackson.databind; // Set up JSON mapping
    requires com.fasterxml.uuid; // UUID 5

    requires static org.jspecify;
    requires static dev.lukebemish.polymorphicsignatures;

    requires javalin.openapi.plugin;
    requires javalin.swagger.plugin;
    requires javalin.redoc.plugin;
    requires openapi.generator;
    requires openapi.specification;
    requires annotations;

    opens dev.lukebemish.larder.api to com.fasterxml.jackson.databind;
}
