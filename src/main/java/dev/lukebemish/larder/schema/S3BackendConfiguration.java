package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

public record S3BackendConfiguration(Identifier<Repository> repository, Identifier<S3Backend> backend, String bucket, String prefix) implements Model {
    public record Id(Identifier<Repository> id) implements Identifier.Template<S3BackendConfiguration> {}

    public static final Representation<S3BackendConfiguration> REPRESENTATION = Representation.build(it -> {
        var repository = it.referenceField("repository", () -> Repository.REPRESENTATION, S3BackendConfiguration::repository);
        var backend = it.referenceField("backend", () -> S3Backend.REPRESENTATION, S3BackendConfiguration::backend);
        var bucket = it.field("bucket", DatabasePrimitiveType.VARCHAR, S3BackendConfiguration::bucket);
        var prefix = it.field("prefix", DatabasePrimitiveType.VARCHAR, S3BackendConfiguration::prefix);
        it.id(repository, Id::id);
        return it.build("s3backendconfigurations", result -> new S3BackendConfiguration(
                repository.get(result),
                backend.get(result),
                bucket.get(result),
                prefix.get(result)
        ));
    });
}
