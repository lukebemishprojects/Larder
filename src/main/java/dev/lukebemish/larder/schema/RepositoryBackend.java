package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.api.RepositoryBackendType;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

import java.util.UUID;

public record RepositoryBackend(UUID id, RepositoryBackendType type) implements Model {
    public record Id(UUID id) implements Identifier.Template<RepositoryBackend> {}
    public static final Representation<RepositoryBackend> REPRESENTATION = Representation.build(it -> {
        var id = it.field("id", DatabasePrimitiveType.UUID, RepositoryBackend::id);
        var type = it.field("type", DatabasePrimitiveType.SMALL_INT, backend -> (short) backend.type().ordinal());
        it.id(id, Id::id);
        return it.build("repositorybackends", result -> new RepositoryBackend(
                id.get(result),
                RepositoryBackendType.values()[type.get(result)]
        ));
    });
}
