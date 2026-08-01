package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Migrations;
import dev.lukebemish.larder.orm.Representation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

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

                CREATE INDEX s3backends_by_id ON s3backends (id);

                CREATE TABLE IF NOT EXISTS repositories (
                    id uuid NOT NULL,
                    name varchar NOT NULL,
                    supportsmavendeploy boolean NOT NULL,
                    supportspublishportal boolean NOT NULL,
                    expirationdays integer NOT NULL,
                    mutable boolean NOT NULL,
                    backend uuid NOT NULL,
                    supportssnapshots boolean NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE name,
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );

                CREATE INDEX repositories_by_backend ON repositories (backend);

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
                    identifiertype smallint NOT NULL,
                    identifiergroup varchar NOT NULL,
                    identifiername varchar NOT NULL,
                    identifierversion varchar NOT NULL,
                    PRIMARY KEY (deployment, identifiertype, identifiergroup, identifiername, identifierversion),
                    FOREIGN KEY (deployment) REFERENCES deployments (id)
                );

                CREATE INDEX deploymentpackages_by_deployment ON deploymentpackages (deployment);

                CREATE TABLE IF NOT EXISTS filesystembackends (
                    id uuid NOT NULL,
                    location varchar,
                    PRIMARY KEY (id),
                    FOREIGN KEY (id) REFERENCES repositorybackends (id)
                );

                CREATE INDEX filesystembackends_by_id ON filesystembackends (id);

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
                    UNIQUE key,
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
                    locationpath varchar NOT NULL,
                    locationname varchar NOT NULL,
                    isdirectory boolean NOT NULL,
                    lastmodified timestamp,
                    size bigint,
                    expires bigint NOT NULL,
                    PRIMARY KEY (repository, locationpath, locationname),
                    FOREIGN KEY (repository) REFERENCES repositories (id)
                );

                CREATE INDEX repositoryindices_by_repository ON repositoryindices (repository);

                CREATE INDEX repositoryindices_by_repository_and_path ON repositoryindices (repository, locationpath);

                CREATE INDEX repositoryindices_by_unique ON repositoryindices (repository, locationpath, locationname);

                CREATE TABLE IF NOT EXISTS tokennamespaces (
                    token uuid NOT NULL,
                    namespace varchar NOT NULL,
                    PRIMARY KEY (token, namespace),
                    FOREIGN KEY (token) REFERENCES accesstokens (id)
                );

                CREATE INDEX tokennamespaces_by_token ON tokennamespaces (token);

                CREATE TABLE IF NOT EXISTS filesystembackendconfigurations (
                    id uuid NOT NULL,
                    backend uuid NOT NULL,
                    prefix varchar NOT NULL,
                    PRIMARY KEY (id),
                    FOREIGN KEY (id) REFERENCES repositories (id),
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );

                CREATE INDEX filesystembackendconfigurations_by_id ON filesystembackendconfigurations (id);

                CREATE TABLE IF NOT EXISTS packages (
                    repository uuid NOT NULL,
                    identifiertype smallint NOT NULL,
                    identifiergroup varchar NOT NULL,
                    identifiername varchar NOT NULL,
                    identifierversion varchar NOT NULL,
                    timestamp timestamp NOT NULL,
                    PRIMARY KEY (repository, identifiertype, identifiergroup, identifiername, identifierversion),
                    FOREIGN KEY (repository) REFERENCES repositories (id)
                );

                CREATE TABLE IF NOT EXISTS usernamespacesunconfirmed (
                    id uuid NOT NULL,
                    namespace varchar NOT NULL,
                    confirmed boolean NOT NULL,
                    PRIMARY KEY (id, namespace),
                    FOREIGN KEY (id) REFERENCES users (id)
                );

                CREATE INDEX usernamespacesunconfirmed_by_user ON usernamespacesunconfirmed (id);

                CREATE INDEX usernamespacesunconfirmed_by_pair ON usernamespacesunconfirmed (id, namespace);

                CREATE TABLE IF NOT EXISTS s3backendconfigurations (
                    id uuid NOT NULL,
                    backend uuid NOT NULL,
                    bucket varchar NOT NULL,
                    prefix varchar NOT NULL,
                    PRIMARY KEY (id),
                    FOREIGN KEY (id) REFERENCES repositories (id),
                    FOREIGN KEY (backend) REFERENCES repositorybackends (id)
                );

                CREATE INDEX s3backendconfigurations_by_id ON s3backendconfigurations (id);""")
            .downgrade(1, """

                """) // TODO: fill
            .build();

    public static final int CURRENT_VERSION = 1;
}
