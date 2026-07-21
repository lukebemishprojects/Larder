package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

public record FilesystemBackendConfiguration(Identifier<Repository> repository, Identifier<FilesystemBackend> backend, String prefix) implements Model {
    public record Id(Identifier<Repository> id) implements Identifier.Template<FilesystemBackendConfiguration> {}

    public static final Representation<FilesystemBackendConfiguration> REPRESENTATION = Representation.build(it -> {
        var repository = it.referenceField("repository", () -> Repository.REPRESENTATION, FilesystemBackendConfiguration::repository);
        var backend = it.referenceField("backend", () -> FilesystemBackend.REPRESENTATION, FilesystemBackendConfiguration::backend);
        var prefix = it.field("prefix", DatabasePrimitiveType.VARCHAR, FilesystemBackendConfiguration::prefix);
        it.id(repository, Id::id);
        return it.build("filesystembackendconfigurations", result -> new FilesystemBackendConfiguration(
                repository.get(result),
                backend.get(result),
                prefix.get(result)
        ));
    });
}
