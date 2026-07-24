package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Migrations;
import dev.lukebemish.larder.orm.Representation;

import java.util.List;

public class Schema {
    private static final List<Representation<?>> SCHEMA_REPRESENTATIONS = List.of(
        User.REPRESENTATION,
        UserNamespace.REPRESENTATION,
        RepositoryBackend.REPRESENTATION,
        S3Backend.REPRESENTATION,
        FilesystemBackend.REPRESENTATION,
        Repository.REPRESENTATION,
        RepositoryIndex.REPRESENTATION,
        S3BackendConfiguration.REPRESENTATION,
        FilesystemBackendConfiguration.REPRESENTATION
    );

    static void main() {
        for (var repr : SCHEMA_REPRESENTATIONS) {
            System.out.println(repr.schema());
        }
    }

    public static final Migrations MIGRATIONS = new Migrations.Builder()
            .upgrade(1, """
                CREATE TABLE IF NOT EXISTS users (
                    email varchar NOT NULL,
                    id uuid NOT NULL,
                    PRIMARY KEY (id)
                );

                CREATE TABLE IF NOT EXISTS usernamespaces (
                    id uuid NOT NULL,
                    namespace varchar NOT NULL,
                    confirmed boolean NOT NULL,
                    PRIMARY KEY (id, namespace),
                    FOREIGN KEY (id) REFERENCES users (id)
                );

                CREATE TABLE IF NOT EXISTS repositorybackends (
                    id uuid NOT NULL,
                    type smallint NOT NULL,
                    PRIMARY KEY (id)
                );

                CREATE TABLE IF NOT EXISTS s3backends (
                    id uuid NOT NULL,
                    region varchar NOT NULL,
                    endpoint varchar NOT NULL,
                    accesskeyid varchar NOT NULL,
                    secretaccesskey varchar NOT NULL,
                    PRIMARY KEY (id),
                    FOREIGN KEY (id) REFERENCES repositorybackends (id)
                );

                CREATE TABLE IF NOT EXISTS filesystembackends (
                    id uuid NOT NULL,
                    location varchar NOT NULL,
                    PRIMARY KEY (id),
                    FOREIGN KEY (id) REFERENCES repositorybackends (id)
                );

                CREATE TABLE IF NOT EXISTS repositories (
                    name varchar NOT NULL,
                    supportsmavendeploy boolean NOT NULL,
                    supportspublishportal boolean NOT NULL,
                    expirationdays integer NOT NULL,
                    mutable boolean NOT NULL,
                    backend uuid NOT NULL,
                    PRIMARY KEY (name),
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );

                CREATE TABLE IF NOT EXISTS repositoryindices (
                    repository varchar NOT NULL,
                    path varchar NOT NULL,
                    name varchar NOT NULL,
                    isdirectory boolean NOT NULL,
                    lastmodified timestamp,
                    size bigint,
                    expires bigint NOT NULL,
                    PRIMARY KEY (repository, path, name),
                    FOREIGN KEY (repository) REFERENCES repositories (name)
                );

                CREATE TABLE IF NOT EXISTS s3backendconfigurations (
                    repository varchar NOT NULL,
                    backend uuid NOT NULL,
                    bucket varchar NOT NULL,
                    prefix varchar NOT NULL,
                    PRIMARY KEY (repository),
                    FOREIGN KEY (repository) REFERENCES repositories (name),
                    FOREIGN KEY (backend) REFERENCES s3backends (id)
                );

                CREATE TABLE IF NOT EXISTS filesystembackendconfigurations (
                    repository varchar NOT NULL,
                    backend uuid NOT NULL,
                    prefix varchar NOT NULL,
                    PRIMARY KEY (repository),
                    FOREIGN KEY (repository) REFERENCES repositories (name),
                    FOREIGN KEY (backend) REFERENCES filesystembackends (id)
                );
                """)
            .downgrade(1, """
                DROP TABLE IF EXISTS users;
                DROP TABLE IF EXISTS usernamespaces;
                DROP TABLE IF EXISTS repositorybackends;
                DROP TABLE IF EXISTS s3backends;
                DROP TABLE IF EXISTS filesystembackends;
                DROP TABLE IF EXISTS repositories;
                DROP TABLE IF EXISTS repositoryindices;
                DROP TABLE IF EXISTS s3backendconfigurations;
                DROP TABLE IF EXISTS filesystembackendconfigurations;
                """)
            .build();

    public static final int CURRENT_VERSION = 1;
}
