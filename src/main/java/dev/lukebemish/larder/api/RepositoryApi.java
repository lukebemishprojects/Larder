package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;

import java.util.UUID;

public record RepositoryApi(
    String name,
    @JsonProperty("supportsmavendeploy") boolean supportsMavenDeploy,
    @JsonProperty("supportspublishportal") boolean supportsPublishPortal,
    @JsonProperty("expirationdays") int expirationDays,
    boolean mutable,
    UUID backend
) {
    public static RepositoryApi from(Repository repository) {
        return new RepositoryApi(
            repository.name(),
            repository.supportsMavenDeploy(),
            repository.supportsPublishPortal(),
            repository.expirationDays(),
            repository.mutable(),
            Identifier.<RepositoryBackend, RepositoryBackend.Id>template(repository.backend()).id()
        );
    }
}
