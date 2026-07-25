package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.api.Location;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

import java.util.Optional;

public record FilesystemBackend(
    Identifier<RepositoryBackend> id,
    Optional<Location> location
) implements Model, Backend<FilesystemBackendConfiguration> {
    public record Id(Identifier<RepositoryBackend> id) implements Identifier.Template<FilesystemBackend> {}

    public static final Representation<FilesystemBackend> REPRESENTATION = Representation.build(it -> {
        var id = it.referenceField("id", () -> RepositoryBackend.REPRESENTATION, FilesystemBackend::id);
        var location = it.optionalField("location", DatabasePrimitiveType.VARCHAR, backend -> backend.location().map(Location::name));
        it.id(id, Id::id);
        return it.build("filesystembackends", result -> new FilesystemBackend(
            id.get(result),
            location.get(result).flatMap(name -> {
                try {
                    return Optional.of(Location.valueOf(name));
                } catch (IllegalArgumentException _) {
                    return Optional.empty();
                }
            })
        ));
    });
}
