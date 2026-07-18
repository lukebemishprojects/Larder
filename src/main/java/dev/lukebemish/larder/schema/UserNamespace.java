package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.util.UUID;

public record UserNamespace(Identifier<User> id, String namespace, boolean confirmed) implements Model {
    public record Id(Identifier<User> id, String namespace) implements Identifier.Template<UserNamespace> {}

    public static final Partial<UserNamespace, ByUser> BY_USER = new Partial<>();
    public record ByUser(Identifier<User> id) implements Partial.Value<UserNamespace, ByUser> {
        @Override
        public Partial<UserNamespace, ByUser> type() {
            return BY_USER;
        }
    }

    public static final Representation<UserNamespace> REPRESENTATION = Representation.build(it -> {
        var id = it.referenceField("id", () -> User.REPRESENTATION, UserNamespace::id);
        var namespace = it.field("namespace", DatabasePrimitiveType.VARCHAR, UserNamespace::namespace);
        var confirmed = it.field("confirmed", DatabasePrimitiveType.BOOLEAN, UserNamespace::confirmed);
        it.id(id, Id::id);
        it.id(namespace, Id::namespace);
        it.partial(BY_USER, id, ByUser::id);
        return it.build("usernamespaces", result -> new UserNamespace(
                id.get(result),
                namespace.get(result),
                confirmed.get(result)
        ));
    });

    public UserNamespace withConfirmed() {
        return new UserNamespace(id, namespace, true);
    }
}
