package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.api.Location;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

public record FilesystemBackend(
    Identifier<RepositoryBackend> id,
    Location location
) implements Model {
    public record Id(Identifier<RepositoryBackend> id) implements Identifier.Template<FilesystemBackend> {}

    public static final Representation<FilesystemBackend> REPRESENTATION = Representation.build(it -> {
        var id = it.referenceField("id", () -> RepositoryBackend.REPRESENTATION, FilesystemBackend::id);
        var location = it.field("location", DatabasePrimitiveType.VARCHAR, backend -> backend.location().name());
        it.id(id, Id::id);
        return it.build("filesystembackeds", result -> new FilesystemBackend(
            id.get(result),
            Location.valueOf(location.get(result))
        ));
    });
}
