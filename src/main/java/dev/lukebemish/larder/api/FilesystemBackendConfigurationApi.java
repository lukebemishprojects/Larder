package dev.lukebemish.larder.api;

import dev.lukebemish.larder.schema.FilesystemBackendConfiguration;

public record FilesystemBackendConfigurationApi(
    String prefix
) {
    public static FilesystemBackendConfigurationApi from(FilesystemBackendConfiguration configuration) {
        return new FilesystemBackendConfigurationApi(configuration.prefix());
    }
}
