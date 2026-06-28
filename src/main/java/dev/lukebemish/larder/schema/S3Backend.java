package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;

import java.util.UUID;

public record S3Backend(Identifier<RepositoryBackend> id, String region, String endpoint, String accessKeyId, String secretAccessKey) implements Model {
    public static final Representation<S3Backend> REPRESENTATION = Representation.build(it -> {
        var id = it.referenceField("id", () -> RepositoryBackend.REPRESENTATION, S3Backend::id);
        var region = it.field("region", DatabasePrimitiveType.VARCHAR, S3Backend::region);
        var endpoint = it.field("endpoint", DatabasePrimitiveType.VARCHAR, S3Backend::endpoint);
        var accessKeyId = it.field("accesskeyid", DatabasePrimitiveType.VARCHAR, S3Backend::accessKeyId);
        var secretAccessKey = it.field("secretaccesskey", DatabasePrimitiveType.VARCHAR, S3Backend::secretAccessKey);
        it.id(id);
        return it.build("s3backends", result -> new S3Backend(
                id.get(result),
                region.get(result),
                endpoint.get(result),
                accessKeyId.get(result),
                secretAccessKey.get(result)
        ));
    });
}
