package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record S3BackendConfiguration(Identifier<Repository> id, Identifier<RepositoryBackend> backend, String bucket, String prefix) implements Model.Extension<Repository> {
    public static final Partial<S3BackendConfiguration, ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<Repository> id) implements Model.Extension.ByHost<Repository, S3BackendConfiguration, ById> {
        @Override
        public Partial<S3BackendConfiguration, ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<S3BackendConfiguration> REPRESENTATION = Representation.build(() -> Repository.REPRESENTATION, (it, id) -> {
        var backend = it.referenceField("backend", () -> RepositoryBackend.REPRESENTATION, S3BackendConfiguration::backend);
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
