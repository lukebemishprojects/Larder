package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

public record UserNamespace(Identifier<User> id, String namespace, boolean confirmed) implements Model {
    public static final Representation<UserNamespace> REPRESENTATION = Representation.build(it -> {
        var id = it.referenceField("id", () -> User.REPRESENTATION, UserNamespace::id);
        var namespace = it.field("namespace", DatabasePrimitiveType.VARCHAR, UserNamespace::namespace);
        var confirmed = it.field("confirmed", DatabasePrimitiveType.BOOLEAN, UserNamespace::confirmed);
        it.id(id);
        it.id(namespace);
        return it.build("usernamespaces", result -> new UserNamespace(
                id.get(result),
                namespace.get(result),
                confirmed.get(result)
        ));
    });
}
