package dev.lukebemish.larder.api;

import dev.lukebemish.larder.schema.User;

import java.util.UUID;

public record UserApi(String email, UUID id) {
    public static UserApi from(User user) {
        return new UserApi(
            user.email(),
            user.id()
        );
    }
}
