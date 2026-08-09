package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record FilesystemBackendConfiguration(Identifier<Repository> id, Identifier<RepositoryBackend> backend, String prefix) implements Model.OneToMany<Repository, Identifier<RepositoryBackend>>, Backend.Config<FilesystemBackendConfiguration, FilesystemBackend> {
    public static final Partial<FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<Repository> source, Identifier<RepositoryBackend> value) implements Model.OneToMany.ByPair<Repository, Identifier<RepositoryBackend>, FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> {
        @Override
        public Partial<FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<FilesystemBackendConfiguration> REPRESENTATION = Representation.build(
        Representation.referenceField("id", () -> Repository.REPRESENTATION, FilesystemBackendConfiguration::id),
        Representation.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, FilesystemBackendConfiguration::backend),
        (it, id, backend) -> {
        var prefix = it.field("prefix", DatabasePrimitiveType.VARCHAR, FilesystemBackendConfiguration::prefix);
        it.partial(BY_ID);
        return it.build("filesystembackendconfigurations", result -> new FilesystemBackendConfiguration(
                id.get(result),
                backend.get(result),
                prefix.get(result)
        ));
    });
}
