db -> execute(db, """
CREATE TABLE IF NOT EXISTS users (
    email varchar NOT NULL,
    id uuid NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS usernamespaces (
    id uuid NOT NULL,
    namespace varchar NOT NULL,
    confirmed boolean NOT NULL,
    FOREIGN KEY (id) REFERENCES users (id),
    PRIMARY KEY (id, namespace)
);

CREATE TABLE IF NOT EXISTS repositorybackends (
    id uuid NOT NULL,
    type integer NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS s3backends (
    id uuid NOT NULL,
    region varchar NOT NULL,
    endpoint varchar NOT NULL,
    accesskeyid varchar NOT NULL,
    secretaccesskey varchar NOT NULL,
    FOREIGN KEY (id) REFERENCES repositorybackends (id),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS repositories (
    name varchar NOT NULL,
    supportsmavendeploy boolean NOT NULL,
    supportspublishportal boolean NOT NULL,
    expirationdays integer NOT NULL,
    mutable boolean NOT NULL,
    backend uuid NOT NULL,
    FOREIGN KEY (backend) REFERENCES repositorybackends (id),
    PRIMARY KEY (name)
);

CREATE TABLE IF NOT EXISTS repositoryindices (
    repository varchar NOT NULL,
    path varchar NOT NULL,
    name varchar NOT NULL,
    isdirectory boolean NOT NULL,
    lastmodified timestamp,
    size bigint,
    expires bigint NOT NULL,
    FOREIGN KEY (repository) REFERENCES repositories (name),
    PRIMARY KEY (repository, path, name)
);

CREATE TABLE IF NOT EXISTS s3backendconfigurations (
    repository varchar NOT NULL,
    backend uuid NOT NULL,
    bucket varchar NOT NULL,
    prefix varchar NOT NULL,
    FOREIGN KEY (repository) REFERENCES repositories (name),
    FOREIGN KEY (backend) REFERENCES s3backends (id),
    PRIMARY KEY (repository)
);

"""),
db -> execute(db, """
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS usernamespaces;
DROP TABLE IF EXISTS repositorybackends;
DROP TABLE IF EXISTS s3backends;
DROP TABLE IF EXISTS repositories;
DROP TABLE IF EXISTS repositoryindices;
DROP TABLE IF EXISTS s3backendconfigurations;
""")