package dev.lukebemish.larder.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

public record Repository(
    String name,
    @JsonProperty("supportsmavendeploy") boolean supportsMavenDeploy,
    @JsonProperty("supportspublishportal") boolean supportsPublishPortal,
    @JsonProperty("expirationdays") int expirationDays,
    boolean mutable,
    Identifier<RepositoryBackend> backend
) implements Model {
    public record Id(String name) implements Identifier.Template<Repository> {}

    public static final Representation<Repository> REPRESENTATION = Representation.build(it -> {
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, Repository::name);
        var supportsMavenDeploy = it.field("supportsmavendeploy", DatabasePrimitiveType.BOOLEAN, Repository::supportsMavenDeploy);
        var supportsPublishPortal = it.field("supportspublishportal", DatabasePrimitiveType.BOOLEAN, Repository::supportsPublishPortal);
        var expirationDays = it.field("expirationdays", DatabasePrimitiveType.INTEGER, Repository::expirationDays);
        var mutable = it.field("mutable", DatabasePrimitiveType.BOOLEAN, Repository::mutable);
        var backend = it.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, Repository::backend);
        it.id(name, Id::name);
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
