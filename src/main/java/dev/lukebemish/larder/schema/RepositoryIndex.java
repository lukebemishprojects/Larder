package dev.lukebemish.larder.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

import java.time.LocalDateTime;
import java.util.Optional;

public record RepositoryIndex(
    Identifier<Repository> repository,
    String path,
    String name,
    @JsonProperty("isdirectory") boolean isDirectory,
    @JsonProperty("lastmodified") Optional<LocalDateTime> lastModified,
    Optional<Long> size,
    long expires
) implements Model {
    public static final Representation<RepositoryIndex> REPRESENTATION = Representation.build(it -> {
        var repository = it.referenceField("repository", () -> Repository.REPRESENTATION, RepositoryIndex::repository);
        var path = it.field("path", DatabasePrimitiveType.VARCHAR, RepositoryIndex::path);
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, RepositoryIndex::name);
        var isDirectory = it.field("isdirectory", DatabasePrimitiveType.BOOLEAN, RepositoryIndex::isDirectory);
        var lastModified = it.optionalField("lastmodified", DatabasePrimitiveType.TIMESTAMP, RepositoryIndex::lastModified);
        var size = it.optionalField("size", DatabasePrimitiveType.BIGINT, RepositoryIndex::size);
        var expires = it.field("expires", DatabasePrimitiveType.BIGINT, RepositoryIndex::expires);
        it.id(repository);
        it.id(path);
        it.id(name);
        return it.build("repositoryindices", result -> new RepositoryIndex(
                repository.get(result),
                path.get(result),
                name.get(result),
                isDirectory.get(result),
                lastModified.get(result),
                size.get(result),
                expires.get(result)
        ));
    });
}
