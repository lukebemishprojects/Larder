package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Migrations;

public class Schema {
    static void main() {
        for (var repr : LarderWorld.INSTANCE.types()) {
            System.out.println(repr.schema());
        }
    }

    public static final Migrations MIGRATIONS = new Migrations.Builder()
            .upgrade(1, """
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

                CREATE TABLE IF NOT EXISTS repositories (
                    id uuid NOT NULL,
                    name varchar NOT NULL,
                    supportsmavendeploy boolean NOT NULL,
                    supportspublishportal boolean NOT NULL,
                    deploymentbackend uuid,
                    expirationdays integer NOT NULL,
                    mutable boolean NOT NULL,
                    backend uuid NOT NULL,
                    supportssnapshots boolean NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE (name),
                    FOREIGN KEY (deploymentbackend) REFERENCES repositorybackends (id),
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );

                CREATE INDEX repositories_by_backend ON repositories (backend);

                CREATE INDEX repositories_by_name ON repositories (name);

                CREATE TABLE IF NOT EXISTS users (
                    id uuid NOT NULL,
                    email varchar NOT NULL,
                    PRIMARY KEY (id)
                );

                CREATE TABLE IF NOT EXISTS deployments (
                    id uuid NOT NULL,
                    target uuid NOT NULL,
                    owner uuid NOT NULL,
                    automatic boolean NOT NULL,
                    name varchar NOT NULL,
                    state smallint NOT NULL,
                    PRIMARY KEY (id),
                    FOREIGN KEY (target) REFERENCES repositories (id),
                    FOREIGN KEY (owner) REFERENCES users (id)
                );

                CREATE INDEX deployments_by_repository ON deployments (target);

                CREATE TABLE IF NOT EXISTS deploymentpackages (
                    deployment uuid NOT NULL,
                    identifier_type smallint NOT NULL,
                    identifier_group varchar NOT NULL,
                    identifier_name varchar NOT NULL,
                    identifier_version varchar NOT NULL,
                    PRIMARY KEY (deployment, identifier_type, identifier_group, identifier_name, identifier_version),
                    FOREIGN KEY (deployment) REFERENCES deployments (id)
                );

                CREATE INDEX deploymentpackages_by_deployment ON deploymentpackages (deployment);

                CREATE TABLE IF NOT EXISTS filesystembackends (
                    id uuid NOT NULL,
                    location varchar,
                    PRIMARY KEY (id),
                    FOREIGN KEY (id) REFERENCES repositorybackends (id)
                );

                CREATE TABLE IF NOT EXISTS accesstokens (
                    id uuid NOT NULL,
                    key varchar NOT NULL,
                    salt bytea NOT NULL,
                    hash bytea NOT NULL,
                    humanname varchar NOT NULL,
                    owner uuid NOT NULL,
                    expiry timestamp NOT NULL,
                    canpublish boolean NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE (key),
                    FOREIGN KEY (owner) REFERENCES users (id)
                );

                CREATE INDEX accesstokens_by_key ON accesstokens (key);

                CREATE INDEX accesstokens_by_owner ON accesstokens (owner);

                CREATE TABLE IF NOT EXISTS tokenrepositories (
                    token uuid NOT NULL,
                    repository uuid NOT NULL,
                    PRIMARY KEY (token, repository),
                    FOREIGN KEY (token) REFERENCES accesstokens (id),
                    FOREIGN KEY (repository) REFERENCES repositories (id)
                );

                CREATE INDEX tokenrepositories_by_repository ON tokenrepositories (repository);

                CREATE INDEX tokenrepositories_by_token ON tokenrepositories (token);

                CREATE TABLE IF NOT EXISTS repositoryindices (
                    repository uuid NOT NULL,
                    location_path varchar NOT NULL,
                    location_name varchar NOT NULL,
                    isdirectory boolean NOT NULL,
                    lastmodified timestamp,
                    size bigint,
                    expires bigint NOT NULL,
                    PRIMARY KEY (repository, location_path, location_name),
                    FOREIGN KEY (repository) REFERENCES repositories (id)
                );

                CREATE INDEX repositoryindices_by_repository ON repositoryindices (repository);

                CREATE INDEX repositoryindices_by_repository_and_path ON repositoryindices (repository, location_path);

                CREATE TABLE IF NOT EXISTS tokennamespaces (
                    token uuid NOT NULL,
                    namespace varchar NOT NULL,
                    PRIMARY KEY (token, namespace),
                    FOREIGN KEY (token) REFERENCES accesstokens (id)
                );

                CREATE INDEX tokennamespaces_by_token ON tokennamespaces (token);

                CREATE TABLE IF NOT EXISTS filesystembackendconfigurations (
                    id uuid NOT NULL,
                    type_id smallint NOT NULL,
                    backend uuid NOT NULL,
                    prefix varchar NOT NULL,
                    PRIMARY KEY (id, type_id),
                    FOREIGN KEY (id) REFERENCES repositories (id),
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );

                CREATE TABLE IF NOT EXISTS packages (
                    repository uuid NOT NULL,
                    identifier_type smallint NOT NULL,
                    identifier_group varchar NOT NULL,
                    identifier_name varchar NOT NULL,
                    identifier_version varchar NOT NULL,
                    timestamp timestamp NOT NULL,
                    PRIMARY KEY (repository, identifier_type, identifier_group, identifier_name, identifier_version),
                    FOREIGN KEY (repository) REFERENCES repositories (id)
                );

                CREATE TABLE IF NOT EXISTS usernamespaces (
                    id uuid NOT NULL,
                    namespace varchar NOT NULL,
                    confirmed boolean NOT NULL,
                    PRIMARY KEY (id, namespace),
                    FOREIGN KEY (id) REFERENCES users (id)
                );

                CREATE INDEX usernamespaces_by_user ON usernamespaces (id);

                CREATE TABLE IF NOT EXISTS s3backendconfigurations (
                    id uuid NOT NULL,
                    type_id smallint NOT NULL,
                    backend uuid NOT NULL,
                    bucket varchar NOT NULL,
                    prefix varchar NOT NULL,
                    PRIMARY KEY (id, type_id),
                    FOREIGN KEY (id) REFERENCES repositories (id),
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );""")
            .downgrade(1, """

                """) // TODO: fill
            .build();

    public static final int CURRENT_VERSION = 1;
}
