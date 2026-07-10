package dev.lukebemish.larder.api;

import dev.lukebemish.larder.schema.RepositoryBackend;

import java.util.UUID;

public record RepositoryBackendApi(UUID id, RepositoryBackendType type) {
    public static RepositoryBackendApi from(RepositoryBackend repositoryBackend) {
        return new RepositoryBackendApi(
            repositoryBackend.id(),
            repositoryBackend.type()
        );
    }
}
