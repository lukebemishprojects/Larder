package dev.lukebemish.larder.api;

import dev.lukebemish.larder.schema.S3BackendConfiguration;

public record S3BackendConfigurationApi(
    String bucket,
    String prefix
) {
    public static S3BackendConfigurationApi from(S3BackendConfiguration configuration) {
        return new S3BackendConfigurationApi(configuration.bucket(), configuration.prefix());
    }
}
