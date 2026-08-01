package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccessToken(UUID id, String key, byte[] salt, byte[] hash, String humanName, Identifier<User> owner, LocalDateTime expiry, boolean canPublish) implements Model.Object {
    public static final Partial<AccessToken, ByOwner> BY_OWNER = new Partial<>("by_owner");
    public record ByOwner(Identifier<User> owner) implements Partial.Value<AccessToken, ByOwner> {
        @Override
        public Partial<AccessToken, ByOwner> type() {
            return BY_OWNER;
        }
    }

    public static final Partial<AccessToken, ByKey> BY_KEY = new Partial<>("by_key");
    public record ByKey(String key) implements Partial.Value<AccessToken, ByKey> {
        @Override
        public Partial<AccessToken, ByKey> type() {
            return BY_KEY;
        }
    }

    public static final Representation<AccessToken> REPRESENTATION = Representation.build((it, id) -> {
        var key = it.field("key", DatabasePrimitiveType.VARCHAR, AccessToken::key);
        var salt = it.field("salt", DatabasePrimitiveType.BYTEA, AccessToken::salt);
        var hash = it.field("hash",  DatabasePrimitiveType.BYTEA, AccessToken::hash);
        var humanName = it.field("humanname", DatabasePrimitiveType.VARCHAR, AccessToken::humanName);
        var owner = it.referenceField("owner", () -> User.REPRESENTATION, AccessToken::owner);
        var expiry = it.field("expiry", DatabasePrimitiveType.TIMESTAMP, AccessToken::expiry);
        var canPublish = it.field("canpublish", DatabasePrimitiveType.BOOLEAN, AccessToken::canPublish);

        it.partial(BY_KEY, key, ByKey::key);
        it.partial(BY_OWNER, owner, ByOwner::owner);

        it.unique(key);

        return it.build("accesstokens", result -> new AccessToken(
            id.get(result),
            key.get(result),
            salt.get(result),
            hash.get(result),
            humanName.get(result),
            owner.get(result),
            expiry.get(result),
            canPublish.get(result)
        ));
    });
}
