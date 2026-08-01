package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record FilesystemBackendConfiguration(Identifier<Repository> id, Identifier<RepositoryBackend> backend, String prefix) implements Model.Extension<Repository> {
    public static final Partial<FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<Repository> id) implements Model.Extension.ByHost<Repository, FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> {
        @Override
        public Partial<FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<FilesystemBackendConfiguration> REPRESENTATION = Representation.build(() -> Repository.REPRESENTATION, (it, id) -> {
        var backend = it.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, FilesystemBackendConfiguration::backend);
        var prefix = it.field("prefix", DatabasePrimitiveType.VARCHAR, FilesystemBackendConfiguration::prefix);
        it.partial(BY_ID);
        return it.build("filesystembackendconfigurations", result -> new FilesystemBackendConfiguration(
                id.get(result),
                backend.get(result),
                prefix.get(result)
        ));
    });
}
