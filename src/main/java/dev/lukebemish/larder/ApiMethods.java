package dev.lukebemish.larder;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.User;

import java.sql.SQLException;
import java.util.UUID;

final class ApiMethods {
    private ApiMethods() {}

    static final UUID UUID_ISS = UUID.fromString("f26ee10c-dfd1-4aff-99f2-03140ad59e46");

    public static User newUser(ModelConnection connection, User user) throws SQLException {
        return connection.transact(c -> {
            var existing = User.REPRESENTATION.find(c, new Identifier<>(user, User.REPRESENTATION));
            if (existing.isEmpty()) {
                User.REPRESENTATION.insert(c, user);
            } else {
                User.REPRESENTATION.update(c, user);
            }
            return User.REPRESENTATION.select(c, new Identifier<>(user, User.REPRESENTATION));
        });
    }
}
