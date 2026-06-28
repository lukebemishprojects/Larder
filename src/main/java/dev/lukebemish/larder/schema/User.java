package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

import java.util.UUID;

public record User(String email, UUID id) implements Model {
    public static final Representation<User> REPRESENTATION = Representation.build(it -> {
        var email = it.field("email", DatabasePrimitiveType.VARCHAR, User::email);
        var id = it.field("id", DatabasePrimitiveType.UUID, User::id);
        it.id(id);
        return it.build("users", result -> new User(
                email.get(result),
                id.get(result)
        ));
    });
}
