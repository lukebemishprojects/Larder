package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record UserNamespace(Identifier<User> source, String value, boolean confirmed) implements Model.OneToMany<User, String> {
    public static final Partial<UserNamespace, ByUser> BY_USER = new Partial<>("by_user");
    public record ByUser(Identifier<User> source) implements BySource<User, UserNamespace, ByUser> {
        @Override
        public Partial<UserNamespace, ByUser> type() {
            return BY_USER;
        }
    }

    public static final Partial<UserNamespace, ByPair> BY_PAIR = new Partial<>("by_pair");
    public record ByPair(Identifier<User> source, String value) implements Model.OneToMany.ByPair<User, String, UserNamespace, ByPair> {
        @Override
        public Partial<UserNamespace, ByPair> type() {
            return BY_PAIR;
        }
    }

    public static final Representation<UserNamespace> REPRESENTATION = Representation.build(
        Representation.referenceField("id", () -> User.REPRESENTATION, UserNamespace::source),
        Representation.field("namespace", DatabasePrimitiveType.VARCHAR, UserNamespace::value),
        (it, id, namespace) -> {
            var confirmed = it.field("confirmed", DatabasePrimitiveType.BOOLEAN, UserNamespace::confirmed);
            it.partialSource(BY_USER);
            it.partial(BY_PAIR);
            return it.build("usernamespacesunconfirmed", result -> new UserNamespace(
                id.get(result),
                namespace.get(result),
                confirmed.get(result)
            ));
        });

    public UserNamespace withConfirmed() {
        return new UserNamespace(source, value, confirmed);
    }
}
