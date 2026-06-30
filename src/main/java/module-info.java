module dev.lukebemish.larder {
    requires io.javalin;
    requires com.fasterxml.jackson.databind; // Set up JSON mapping

    requires static org.jspecify;
    requires java.sql;
    requires com.fasterxml.uuid;

    exports dev.lukebemish.larder.api to com.fasterxml.jackson.databind;
}
