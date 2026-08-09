package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

public record S3Backend(
    Identifier<RepositoryBackend> id,
    String region,
    String endpoint,
    String accessKeyId,
    String secretAccessKey
) implements Model.Extension<RepositoryBackend>, Backend<S3BackendConfiguration, S3Backend> {
    public static final Partial<S3Backend, S3Backend.ById> BY_ID = new Partial<>("by_id");

    @Override
    public @Nullable InputStream readPath(S3BackendConfiguration config, String relativePath) {
        throw new RuntimeException("Not Yet Implemented");
    }

    @Override
    public OutputStream writePath(S3BackendConfiguration config, String relativePath) {
        throw new RuntimeException("Not Yet Implemented");
    }

    public record ById(Identifier<RepositoryBackend> id) implements Model.Extension.ByHost<RepositoryBackend, S3Backend, S3Backend.ById> {
        @Override
        public Partial<S3Backend, S3Backend.ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<S3Backend> REPRESENTATION = Representation.build(() -> RepositoryBackend.REPRESENTATION, (it, id) -> {
        var region = it.field("region", DatabasePrimitiveType.VARCHAR, S3Backend::region);
        var endpoint = it.field("endpoint", DatabasePrimitiveType.VARCHAR, S3Backend::endpoint);
        var accessKeyId = it.field("accesskeyid", DatabasePrimitiveType.VARCHAR, S3Backend::accessKeyId);
        var secretAccessKey = it.field("secretaccesskey", DatabasePrimitiveType.VARCHAR, S3Backend::secretAccessKey);
        it.partial(BY_ID);
        return it.build("s3backends", result -> new S3Backend(
                id.get(result),
                region.get(result),
                endpoint.get(result),
                accessKeyId.get(result),
                secretAccessKey.get(result)
        ));
    });
}
