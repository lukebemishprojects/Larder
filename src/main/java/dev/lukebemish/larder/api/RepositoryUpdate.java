package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.RepositoryBackendType;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;

public record RepositoryUpdate(
    String name,
    @JsonProperty("supportsmavendeploy") boolean supportsMavenDeploy,
    @JsonProperty("supportspublishportal") boolean supportsPublishPortal,
    @JsonProperty("expirationdays") int expirationDays,
    boolean mutable,
    Identifier<RepositoryBackend> backend,
    @JsonProperty("s3backend") @Nullable S3BackendConfigurationUpdate s3Backend
) {
    public static RepositoryUpdate from(Repository repository, ModelConnection connection) throws SQLException {
        var backend = connection.select(repository.backend());
        S3BackendConfigurationUpdate s3Backend = null;
        if (backend.type() == RepositoryBackendType.S3) {
            var configuration = connection.select(Identifier.of(new S3BackendConfiguration.Id(Identifier.of(repository))));
            s3Backend = S3BackendConfigurationUpdate.from(configuration);
        }
        return new RepositoryUpdate(
            repository.name(),
            repository.supportsMavenDeploy(),
            repository.supportsPublishPortal(),
            repository.expirationDays(),
            repository.mutable(),
            repository.backend(),
            s3Backend
        );
    }
}
