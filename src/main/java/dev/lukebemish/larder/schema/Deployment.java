package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.api.DeploymentState;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;
import lombok.With;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public record Deployment(UUID id, Identifier<Repository> target, Identifier<User> owner, boolean automatic, String name, DeploymentState state, Optional<LocalDateTime> tryProgressAfter, @With Optional<Identifier<AccessRole>> responsibleRole) implements Model.Object {
    // Queued work responds to the status and tryProgressAfter
    // - when a task begins on the deployment, it will have its tryProgressAfter updated. No other task may execute on
    //   the deployment until the tryProgressAfter is reached. For most statuses this will be short; for some (FAILED)
    //   it will be quite long.
    // - when a deployment is created, work should immediately be PENDING
    // - a "validate" task should respond to PENDING or VALIDATING and move it to either VALIDATED, FAILED, or (if `automatic`) PUBLISHING
    // - a "publish" task should respond to PUBLISHING and move it to PUBLISHED
    // - a "clean up" task should drop any old FAILED deployments after some time
    // - a deployment in PUBLISHED should not be acted on by tasks

    public static final Partial<Deployment, ByRepository> BY_REPOSITORY = new Partial<>("by_repository");
    public record ByRepository(Identifier<Repository> repository) implements Partial.Value<Deployment, ByRepository> {
        @Override
        public Partial<Deployment, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }

    public static final Partial<Deployment, ByResponsibleRole> BY_RESPONSIBLE_ROLE = new Partial<>("by_responsible_role");
    public record ByResponsibleRole(Identifier<AccessRole> role) implements Partial.Value<Deployment, ByResponsibleRole> {
        @Override
        public Partial<Deployment, ByResponsibleRole> type() {
            return BY_RESPONSIBLE_ROLE;
        }
    }

    public static final Representation<Deployment> REPRESENTATION = Representation.build((it, id) -> {
        var target = it.referenceField("target", () -> Repository.REPRESENTATION, Deployment::target);
        var owner = it.referenceField("owner", () -> User.REPRESENTATION, Deployment::owner);
        var automatic = it.field("automatic", DatabasePrimitiveType.BOOLEAN, Deployment::automatic);
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, Deployment::name);
        var state = it.field("state", DatabasePrimitiveType.SMALL_INT, i -> (short) i.state().ordinal());
        var tryProgressAfter = it.optionalField("tryprogressafter", DatabasePrimitiveType.TIMESTAMP, Deployment::tryProgressAfter);
        var responsibleRole = it.optionalReferenceField("responsiblerole", () -> AccessRole.REPRESENTATION, Deployment::responsibleRole);

        it.partial(BY_REPOSITORY, target, ByRepository::repository);

        it.partial(BY_RESPONSIBLE_ROLE, responsibleRole, p -> Optional.of(p.role()));

        return it.build("deployments", result -> new Deployment(
            id.get(result),
            target.get(result),
            owner.get(result),
            automatic.get(result),
            name.get(result),
            DeploymentState.values()[state.get(result)],
            tryProgressAfter.get(result),
            responsibleRole.get(result)
        ));
    });
}
