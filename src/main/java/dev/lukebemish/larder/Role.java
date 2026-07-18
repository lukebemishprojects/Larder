package dev.lukebemish.larder;

import io.javalin.security.RouteRole;

public sealed interface Role extends RouteRole {
    enum Builtin implements Role {
        ADMIN,
        USER
    }
}
