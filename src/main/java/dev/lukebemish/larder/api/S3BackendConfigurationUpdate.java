package dev.lukebemish.larder.api;

import dev.lukebemish.larder.schema.S3BackendConfiguration;

public record S3BackendConfigurationUpdate(
    String bucket,
    String prefix
) {
    public static S3BackendConfigurationUpdate from(S3BackendConfiguration configuration) {
        return new S3BackendConfigurationUpdate(configuration.bucket(), configuration.prefix());
    }
}
