package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.UUID;

public record RepositoryApi(
    String name,
    @JsonProperty("supportsmavendeploy") @OpenApiName("supportsmavendeploy") boolean supportsMavenDeploy,
    @JsonProperty("supportspublishportal") @OpenApiName("supportspublishportal") boolean supportsPublishPortal,
    @JsonProperty("expirationdays") @OpenApiName("expirationdays") int expirationDays,
    boolean mutable,
    UUID backend,
    @JsonProperty("s3backend") @Nullable S3BackendConfigurationApi s3Backend
) {
    public static RepositoryApi from(Repository repository, ModelConnection connection) throws SQLException {
        var backend = connection.select(repository.backend());
        S3BackendConfigurationApi s3Backend = null;
        if (backend.type() == RepositoryBackendType.S3) {
            var configuration = connection.select(Identifier.of(new S3BackendConfiguration.Id(Identifier.of(repository))));
            s3Backend = S3BackendConfigurationApi.from(configuration);
        }
        return new RepositoryApi(
            repository.name(),
            repository.supportsMavenDeploy(),
            repository.supportsPublishPortal(),
            repository.expirationDays(),
            repository.mutable(),
            Identifier.<RepositoryBackend, RepositoryBackend.Id>template(repository.backend()).id(),
            s3Backend
        );
    }
}
