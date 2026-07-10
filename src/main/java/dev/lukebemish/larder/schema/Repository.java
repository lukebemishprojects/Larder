package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record Repository(
    String name,
    boolean supportsMavenDeploy,
    boolean supportsPublishPortal,
    int expirationDays,
    boolean mutable,
    Identifier<RepositoryBackend> backend
) implements Model {
    public record Id(String name) implements Identifier.Template<Repository> {}

    public static final Partial<Repository, Repository.ByBackend> BY_BACKEND = new Partial<>();
    public record ByBackend(Identifier<RepositoryBackend> repository) implements Partial.Value<Repository, Repository.ByBackend> {
        @Override
        public Partial<Repository, ByBackend> type() {
            return BY_BACKEND;
        }
    }

    public static final Representation<Repository> REPRESENTATION = Representation.build(it -> {
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, Repository::name);
        var supportsMavenDeploy = it.field("supportsmavendeploy", DatabasePrimitiveType.BOOLEAN, Repository::supportsMavenDeploy);
        var supportsPublishPortal = it.field("supportspublishportal", DatabasePrimitiveType.BOOLEAN, Repository::supportsPublishPortal);
        var expirationDays = it.field("expirationdays", DatabasePrimitiveType.INTEGER, Repository::expirationDays);
        var mutable = it.field("mutable", DatabasePrimitiveType.BOOLEAN, Repository::mutable);
        var backend = it.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, Repository::backend);
        it.id(name, Id::name);

        it.partial(BY_BACKEND, backend, ByBackend::repository);

        return it.build("repositories", result -> new Repository(
                name.get(result),
                supportsMavenDeploy.get(result),
                supportsPublishPortal.get(result),
                expirationDays.get(result),
                mutable.get(result),
                backend.get(result)
        ));
    });
}
