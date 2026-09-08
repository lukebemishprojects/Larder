package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefreshToken(UUID id, Identifier<User> owner, String key, byte[] salt, byte[] hash, LocalDateTime expiry, String refreshToken) implements Model.Object {
    public static final Partial<RefreshToken, ByKey> BY_KEY = new Partial<>("by_key");
    public record ByKey(String key) implements Partial.Value<RefreshToken, ByKey> {
        @Override
        public Partial<RefreshToken, ByKey> type() {
            return BY_KEY;
        }
    }

    public static final Partial<RefreshToken, ByOwner> BY_OWNER = new Partial<>("by_owner");
    public record ByOwner(Identifier<User> owner) implements Partial.Value<RefreshToken, ByOwner> {
        @Override
        public Partial<RefreshToken, ByOwner> type() {
            return BY_OWNER;
        }
    }

    public static final Representation<RefreshToken> REPRESENTATION = Representation.build((it, id) -> {
        var owner = it.referenceField("owner", () -> User.REPRESENTATION, RefreshToken::owner);
        var key = it.field("key", DatabasePrimitiveType.VARCHAR, RefreshToken::key);
        var salt = it.field("salt", DatabasePrimitiveType.BYTEA, RefreshToken::salt);
        var hash = it.field("hash",  DatabasePrimitiveType.BYTEA, RefreshToken::hash);
        var expiry = it.field("expiry", DatabasePrimitiveType.TIMESTAMP, RefreshToken::expiry);
        var refreshToken = it.field("refreshtoken", DatabasePrimitiveType.VARCHAR, RefreshToken::refreshToken);

        it.partial(BY_KEY, key, ByKey::key);
        it.partial(BY_OWNER, owner, ByOwner::owner);

        it.unique(key);

        return it.build("refreshtokens", result -> new RefreshToken(
            id.get(result),
            owner.get(result),
            key.get(result),
            salt.get(result),
            hash.get(result),
            expiry.get(result),
            refreshToken.get(result)
        ));
    });
}
