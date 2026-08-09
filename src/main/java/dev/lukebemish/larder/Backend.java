package dev.lukebemish.larder;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.BackendConfigurationType;
import dev.lukebemish.larder.schema.FilesystemBackend;
import dev.lukebemish.larder.schema.FilesystemBackendConfiguration;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryBackend;
import dev.lukebemish.larder.schema.S3Backend;
import dev.lukebemish.larder.schema.S3BackendConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

public interface Backend<C extends Backend.Config<C, B>, B extends Backend<C, B>> {
    interface Config<C extends Config<C, B>, B extends Backend<C, B>> {}

    record Configured<C extends Config<C, B>, B extends Backend<C, B>>(B backend, C config) {
        public @Nullable InputStream readPath(String relativePath) {
            return backend.readPath(config, relativePath);
        }

        public OutputStream writePath(String relativePath) {
            return backend.writePath(config, relativePath);
        }
    }

    @Nullable InputStream readPath(C config, String relativePath);
    OutputStream writePath(C config, String relativePath);

    static Backend<?, ?> backend(RepositoryBackend backend, ModelConnection connection) throws SQLException {
        return switch (backend.type()) {
            case S3 -> connection.select(new S3Backend.ById(Identifier.of(backend))).getFirst();
            case FILESYSTEM -> connection.select(new FilesystemBackend.ById(Identifier.of(backend))).getFirst();
        };
    }

    static Configured<?, ?> configuredBackend(Identifier<Repository> repository, BackendConfigurationType type, ModelConnection connection) throws SQLException {
        var repositoryObj = connection.select(repository);
        var backendId = switch (type) {
            case PRIMARY -> repositoryObj.backend();
            case DEPLOYMENTS -> repositoryObj.deploymentBackend().orElseThrow();
        };
        var backend = connection.select(backendId);
        return switch (backend.type()) {
            case S3 -> configuredS3Backend(repository, backend, type, connection);
            case FILESYSTEM -> configuredFilesystemBackend(repository, backend, type, connection);
        };
    }

    private static Configured<FilesystemBackendConfiguration,FilesystemBackend> configuredFilesystemBackend(Identifier<Repository> repository, RepositoryBackend backend, BackendConfigurationType type, ModelConnection connection) throws SQLException {
        var filesystemBackend = connection.select(new FilesystemBackend.ById(Identifier.of(backend))).getFirst();
        var filesystemBackendConfig = connection.select(new FilesystemBackendConfiguration.ById(repository, type)).getFirst();
        return new Configured<>(filesystemBackend, filesystemBackendConfig);
    }

    private static Configured<S3BackendConfiguration,S3Backend> configuredS3Backend(Identifier<Repository> repository, RepositoryBackend backend, BackendConfigurationType type, ModelConnection connection) throws SQLException {
        var s3backend = connection.select(new S3Backend.ById(Identifier.of(backend))).getFirst();
        var s3backendConfig = connection.select(new S3BackendConfiguration.ById(repository, type)).getFirst();
        return new Configured<>(s3backend, s3backendConfig);
    }
}
