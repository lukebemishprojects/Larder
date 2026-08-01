package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.api.Location;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;
import dev.lukebemish.larder.utils.Enums;

import java.util.Optional;

public record FilesystemBackend(
    Identifier<RepositoryBackend> id,
    Optional<Location> location
) implements Model.Extension<RepositoryBackend>, Backend<FilesystemBackendConfiguration> {
    public static final Partial<FilesystemBackend, FilesystemBackend.ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<RepositoryBackend> id) implements Model.Extension.ByHost<RepositoryBackend, FilesystemBackend, FilesystemBackend.ById> {
        @Override
        public Partial<FilesystemBackend, FilesystemBackend.ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<FilesystemBackend> REPRESENTATION = Representation.build(() -> RepositoryBackend.REPRESENTATION, (it, id) -> {
        var location = it.optionalField("location", DatabasePrimitiveType.VARCHAR, backend -> backend.location().map(Location::name));
        it.partial(BY_ID);
        return it.build("filesystembackends", result -> new FilesystemBackend(
            id.get(result),
            location.get(result).flatMap(name -> Optional.ofNullable(Enums.tryValueOf(name)))
        ));
    });
}
