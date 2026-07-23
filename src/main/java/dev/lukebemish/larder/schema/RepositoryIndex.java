package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.time.LocalDateTime;
import java.util.Optional;

public record RepositoryIndex(
    Identifier<Repository> repository,
    String path,
    String name,
    boolean isDirectory,
    Optional<LocalDateTime> lastModified,
    Optional<Long> size,
    long expires
) implements Model {
    public record Id(Identifier<Repository> repository, String path, String name) implements Identifier.Template<RepositoryIndex> {}

    public static final Partial<RepositoryIndex, RepositoryIndex.ByRepository> BY_REPOSITORY = new Partial<>();
    public record ByRepository(Identifier<Repository> repository) implements Partial.Value<RepositoryIndex, RepositoryIndex.ByRepository> {
        @Override
        public Partial<RepositoryIndex, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }

    public static final Partial<RepositoryIndex, RepositoryIndex.ByRepositoryAndPath> BY_REPOSITORY_AND_PATH = new Partial<>();
    public record ByRepositoryAndPath(Identifier<Repository> repository, String path) implements Partial.Value<RepositoryIndex, RepositoryIndex.ByRepositoryAndPath> {
        @Override
        public Partial<RepositoryIndex, ByRepositoryAndPath> type() {
            return BY_REPOSITORY_AND_PATH;
        }
    }

    public static final Representation<RepositoryIndex> REPRESENTATION = Representation.build(it -> {
        var repository = it.referenceField("repository", () -> Repository.REPRESENTATION, RepositoryIndex::repository);
        var path = it.field("path", DatabasePrimitiveType.VARCHAR, RepositoryIndex::path);
        var name = it.field("name", DatabasePrimitiveType.VARCHAR, RepositoryIndex::name);
        var isDirectory = it.field("isdirectory", DatabasePrimitiveType.BOOLEAN, RepositoryIndex::isDirectory);
        var lastModified = it.optionalField("lastmodified", DatabasePrimitiveType.TIMESTAMP, RepositoryIndex::lastModified);
        var size = it.optionalField("size", DatabasePrimitiveType.BIGINT, RepositoryIndex::size);
        var expires = it.field("expires", DatabasePrimitiveType.BIGINT, RepositoryIndex::expires);
        it.id(repository, Id::repository);
        it.id(path, Id::path);
        it.id(name, Id::name);

        it.partial(BY_REPOSITORY, repository, ByRepository::repository);

        it.partial(BY_REPOSITORY_AND_PATH, repository, ByRepositoryAndPath::repository);
        it.partial(BY_REPOSITORY_AND_PATH, path, ByRepositoryAndPath::path);

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
