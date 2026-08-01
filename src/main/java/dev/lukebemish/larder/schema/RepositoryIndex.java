package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

import java.time.LocalDateTime;
import java.util.Optional;

public record RepositoryIndex(
    Identifier<Repository> source,
    String path,
    String name,
    boolean isDirectory,
    Optional<LocalDateTime> lastModified,
    Optional<Long> size,
    long expires
) implements Model.OneToMany<Repository, RepositoryIndex.Location> {
    public static final Partial<RepositoryIndex, RepositoryIndex.ByRepository> BY_REPOSITORY = new Partial<>("by_repository");
    public record ByRepository(Identifier<Repository> source) implements Model.OneToMany.BySource<Repository, RepositoryIndex, RepositoryIndex.ByRepository> {
        @Override
        public Partial<RepositoryIndex, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }

    public static final Partial<RepositoryIndex, RepositoryIndex.ByRepositoryAndPath> BY_REPOSITORY_AND_PATH = new Partial<>("by_repository_and_path");
    public record ByRepositoryAndPath(Identifier<Repository> repository, String path) implements Partial.Value<RepositoryIndex, RepositoryIndex.ByRepositoryAndPath> {
        @Override
        public Partial<RepositoryIndex, ByRepositoryAndPath> type() {
            return BY_REPOSITORY_AND_PATH;
        }
    }

    public static final Partial<RepositoryIndex, ByUnique> BY_UNIQUE = new Partial<>("by_unique");
    public record ByUnique(Identifier<Repository> source, String path, String name) implements Model.OneToMany.ByPair<Repository, Location, RepositoryIndex, ByUnique> {
        @Override
        public Partial<RepositoryIndex, ByUnique> type() {
            return BY_UNIQUE;
        }

        @Override
        public Location value() {
            return new Location(path, name);
        }
    }

    public record Location(String path, String name) {}

    public static final Representation<RepositoryIndex> REPRESENTATION = Representation.build(
        Representation.referenceField("repository", () -> Repository.REPRESENTATION, RepositoryIndex::source),
        Representation.grouped("location", repo -> new Location(repo.path(), repo.name()), group -> {
            var path = group.field("path", DatabasePrimitiveType.VARCHAR, Location::path);
            var name = group.field("name", DatabasePrimitiveType.VARCHAR, Location::name);
            return group.build(result -> new Location(
                path.get(result),
                name.get(result)
            ));
        }),
        (it, source, value) -> {
            var isDirectory = it.field("isdirectory", DatabasePrimitiveType.BOOLEAN, RepositoryIndex::isDirectory);
            var lastModified = it.optionalField("lastmodified", DatabasePrimitiveType.TIMESTAMP, RepositoryIndex::lastModified);
            var size = it.optionalField("size", DatabasePrimitiveType.BIGINT, RepositoryIndex::size);
            var expires = it.field("expires", DatabasePrimitiveType.BIGINT, RepositoryIndex::expires);

            it.partial(BY_REPOSITORY, source, ByRepository::source);

            it.partial(BY_REPOSITORY_AND_PATH, source, ByRepositoryAndPath::repository);
            it.partial(BY_REPOSITORY_AND_PATH, value, p -> new Location(p.path(), ""), 0);

            it.partial(BY_UNIQUE);

            return it.build("repositoryindices", result -> new RepositoryIndex(
                    source.get(result),
                    value.get(result).path(),
                    value.get(result).name(),
                    isDirectory.get(result),
                    lastModified.get(result),
                    size.get(result),
                    expires.get(result)
            ));
    });
}
