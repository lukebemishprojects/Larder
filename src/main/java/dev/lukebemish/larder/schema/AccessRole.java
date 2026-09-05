package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

import java.util.UUID;

public record AccessRole(UUID id, Identifier<User> owner, boolean canPublish) implements Model.Object {
    public static final Representation<AccessRole> REPRESENTATION = Representation.build((it, id) -> {
        var owner = it.referenceField("owner", () -> User.REPRESENTATION, AccessRole::owner);
        var canPublish = it.field("canpublish", DatabasePrimitiveType.BOOLEAN, AccessRole::canPublish);

        return it.build("accessroles", result -> new AccessRole(
            id.get(result),
            owner.get(result),
            canPublish.get(result)
        ));
    });
}
