package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record S3BackendConfiguration(Identifier<Repository> id, Identifier<RepositoryBackend> backend, String bucket, String prefix) implements Model.OneToMany<Repository, Identifier<RepositoryBackend>>, Backend.Config<S3BackendConfiguration, S3Backend> {
    public static final Partial<S3BackendConfiguration, ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<Repository> source, Identifier<RepositoryBackend> value) implements Model.OneToMany.ByPair<Repository, Identifier<RepositoryBackend>, S3BackendConfiguration, ById> {
        @Override
        public Partial<S3BackendConfiguration, ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<S3BackendConfiguration> REPRESENTATION = Representation.build(
        Representation.referenceField("id", () -> Repository.REPRESENTATION, S3BackendConfiguration::id),
        Representation.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, S3BackendConfiguration::backend),
        (it, id, backend) -> {
        var bucket = it.field("bucket", DatabasePrimitiveType.VARCHAR, S3BackendConfiguration::bucket);
        var prefix = it.field("prefix", DatabasePrimitiveType.VARCHAR, S3BackendConfiguration::prefix);

        it.partial(BY_ID);

        return it.build("s3backendconfigurations", result -> new S3BackendConfiguration(
                id.get(result),
                backend.get(result),
                bucket.get(result),
                prefix.get(result)
        ));
    });
}
