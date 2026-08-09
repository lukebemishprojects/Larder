package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record FilesystemBackendConfiguration(Identifier<Repository> id, BackendConfigurationType type, Identifier<RepositoryBackend> backend, String prefix) implements Model.OneToMany<Repository, BackendConfigurationType>, Backend.Config<FilesystemBackendConfiguration, FilesystemBackend> {
    public static final Partial<FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<Repository> source, BackendConfigurationType value) implements Model.OneToMany.ByPair<Repository, BackendConfigurationType, FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> {
        @Override
        public Partial<FilesystemBackendConfiguration, FilesystemBackendConfiguration.ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<FilesystemBackendConfiguration> REPRESENTATION = Representation.<Repository, BackendConfigurationType, FilesystemBackendConfiguration>build(
        Representation.referenceField("id", () -> Repository.REPRESENTATION, FilesystemBackendConfiguration::id),
        Representation.grouped("type", FilesystemBackendConfiguration::type, group -> {
            var id = group.field("id", DatabasePrimitiveType.SMALL_INT, it -> (short) it.ordinal());
            return group.build(result -> BackendConfigurationType.values()[id.get(result)]);
        }),
        (it, id, type) -> {
            var backend = it.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, FilesystemBackendConfiguration::backend);
            var prefix = it.field("prefix", DatabasePrimitiveType.VARCHAR, FilesystemBackendConfiguration::prefix);
            it.partial(BY_ID);
            return it.build("filesystembackendconfigurations", result -> new FilesystemBackendConfiguration(
                    id.get(result),
                    type.get(result),
                    backend.get(result),
                    prefix.get(result)
            ));
    });
}
