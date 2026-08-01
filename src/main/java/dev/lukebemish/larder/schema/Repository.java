package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.util.UUID;

public record Repository(
    UUID id,
    String name,
    boolean supportsMavenDeploy,
    boolean supportsPublishPortal,
    int expirationDays,
    boolean mutable,
    Identifier<RepositoryBackend> backend,
    boolean supportsSnapshots
) implements Model.Object {
    public static final Partial<Repository, Repository.ByBackend> BY_BACKEND = new Partial<>("by_backend");
    public record ByBackend(Identifier<RepositoryBackend> repository) implements Partial.Value<Repository, Repository.ByBackend> {
        @Override
        public Partial<Repository, ByBackend> type() {
            return BY_BACKEND;
        }
    }

    public static final Partial<Repository, Repository.ByName> BY_NAME = new Partial<>("by_name");
    public record ByName(String name) implements Partial.Value<Repository, Repository.ByName> {
        @Override
        public Partial<Repository, ByName> type() {
            return BY_NAME;
        }
    }

    public static final Representation<Repository> REPRESENTATION = Representation.build((it, id) -> {
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, Repository::name);
        var supportsMavenDeploy = it.field("supportsmavendeploy", DatabasePrimitiveType.BOOLEAN, Repository::supportsMavenDeploy);
        var supportsPublishPortal = it.field("supportspublishportal", DatabasePrimitiveType.BOOLEAN, Repository::supportsPublishPortal);
        var expirationDays = it.field("expirationdays", DatabasePrimitiveType.INTEGER, Repository::expirationDays);
        var mutable = it.field("mutable", DatabasePrimitiveType.BOOLEAN, Repository::mutable);
        var backend = it.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, Repository::backend);
        var snapshots = it.field("supportssnapshots", DatabasePrimitiveType.BOOLEAN, Repository::supportsSnapshots);

        it.unique(name);

        it.partial(BY_BACKEND, backend, ByBackend::repository);
        it.partial(BY_NAME, name, ByName::name);

        return it.build("repositories", result -> new Repository(
                id.get(result),
                name.get(result),
                supportsMavenDeploy.get(result),
                supportsPublishPortal.get(result),
                expirationDays.get(result),
                mutable.get(result),
                backend.get(result),
            snapshots.get(result)
        ));
    });
}
