package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.api.DeploymentState;
import dev.lukebemish.larder.api.DeploymentStatus;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.sql.SQLException;
import java.util.UUID;

public record Deployment(UUID id, Identifier<Repository> target, Identifier<User> owner, boolean automatic, String name, DeploymentState state) implements Model.Object {
    public static final Partial<Deployment, ByRepository> BY_REPOSITORY = new Partial<>("by_repository");
    public record ByRepository(Identifier<Repository> repository) implements Partial.Value<Deployment, ByRepository> {
        @Override
        public Partial<Deployment, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }

    public void remove(ModelConnection connection) throws SQLException {
        // Clean up on disk...

        // Clean up on database
        // TODO: Instance method calls, broken again, somehow?
        // This is passing a type of "Lremove;"
        // connection.delete(this);
    }

    public static final Representation<Deployment> REPRESENTATION = Representation.build((it, id) -> {
        var target = it.referenceField("target", () -> Repository.REPRESENTATION, Deployment::target);
        var owner = it.referenceField("owner", () -> User.REPRESENTATION, Deployment::owner);
        var automatic = it.field("automatic", DatabasePrimitiveType.BOOLEAN, Deployment::automatic);
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, Deployment::name);
        var state = it.field("state", DatabasePrimitiveType.SMALL_INT, i -> (short) i.state().ordinal());

        it.partial(BY_REPOSITORY, target, ByRepository::repository);

        return it.build("deployments", result -> new Deployment(
            id.get(result),
            target.get(result),
            owner.get(result),
            automatic.get(result),
            name.get(result),
            DeploymentState.values()[state.get(result)]
        ));
    });
}
