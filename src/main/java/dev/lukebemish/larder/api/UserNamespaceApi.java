package dev.lukebemish.larder.api;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.User;
import dev.lukebemish.larder.schema.UserNamespace;

import java.util.UUID;

public record UserNamespaceApi(UUID id, String namespace, boolean confirmed) {
    public static UserNamespaceApi from(UserNamespace userNamespace) {
        return new UserNamespaceApi(
            userNamespace.source().id(),
            userNamespace.value(),
            userNamespace.confirmed()
        );
    }
}
