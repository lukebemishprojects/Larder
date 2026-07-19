package dev.lukebemish.larder;

import io.javalin.security.RouteRole;

import java.util.Locale;

public sealed interface Role extends RouteRole {
    String unique();

    enum Builtin implements Role {
        ADMIN,
        USER;

        @Override
        public String unique() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
