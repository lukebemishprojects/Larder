package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.FilesystemBackendConfiguration;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.sql.SQLException;
import java.util.UUID;

public record RepositoryApi(
    String name,
    @JsonProperty("supportsmavendeploy") @OpenApiName("supportsmavendeploy") boolean supportsMavenDeploy,
    @JsonProperty("supportspublishportal") @OpenApiName("supportspublishportal") boolean supportsPublishPortal,
    @JsonProperty("expirationdays") @OpenApiName("expirationdays") int expirationDays,
    boolean mutable,
    UUID backend,
    @JsonProperty("s3backend") @OpenApiName("s3backend") @JsonInclude(JsonInclude.Include.NON_ABSENT) @Nullable S3BackendConfigurationApi s3Backend,
    @JsonProperty("filesystembackend") @OpenApiName("filesystembackend") @JsonInclude(JsonInclude.Include.NON_ABSENT) @Nullable FilesystemBackendConfigurationApi filesystemBackend
) {
    public static RepositoryApi from(Repository repository, ModelConnection connection) throws SQLException {
        var backend = connection.select(repository.backend());
        S3BackendConfigurationApi s3Backend = null;
        FilesystemBackendConfigurationApi filesystemBackend = null;
        switch (backend.type()) {
            case RepositoryBackendType.S3 -> {
                var configuration = connection.select(Identifier.of(new S3BackendConfiguration.Id(Identifier.of(repository))));
                s3Backend = S3BackendConfigurationApi.from(configuration);
            }
            case RepositoryBackendType.FILESYSTEM -> {
                var configuration = connection.select(Identifier.of(new FilesystemBackendConfiguration.Id(Identifier.of(repository))));
                filesystemBackend = FilesystemBackendConfigurationApi.from(configuration);
            }
        }
        return new RepositoryApi(
            repository.name(),
            repository.supportsMavenDeploy(),
            repository.supportsPublishPortal(),
            repository.expirationDays(),
            repository.mutable(),
            Identifier.<RepositoryBackend, RepositoryBackend.Id>template(repository.backend()).id(),
            s3Backend,
            filesystemBackend
        );
    }
}
