module api

using JSON
using UUIDs
import LibPQ
using LarderORM
using StructTypes

@enum Permission allowdashboard allowadmindashboard

struct Context
    db::LarderORM.Database
end

Base.close(c::Context) = close(c.db)

struct User <: Model
    email::String
    id::UUID
end

LarderORM.tablename(::Type{User}) = "users"
StructTypes.idproperty(::Type{User}) = :id

struct UserNamespace <: Model
    id::Identifier{User}
    namespace::String
end

LarderORM.tablename(::Type{UserNamespace}) = "usernamespaces"

struct Repository <: Model
    name::String
    supportsmavendeploy::Bool
    supportspublishportal::Bool
    expirationdays::Union{Nothing, Int32}
    mutable::Bool
end

LarderORM.tablename(::Type{Repository}) = "repositories"
StructTypes.idproperty(::Type{Repository}) = :name

function printschemas()
    for T in (User, UserNamespace, Repository)
        println(schema(T))
    end
end

function newuser(user, context)
    transact(context.db) do db
        existing = selectmodel(db, Identifier(user))
        if isnothing(existing)
            insertmodel!(db, user)
        elseif existing != user
            update!(db, user)
        end
    end
    user
end

JSON.lower(uuid::UUID) = string(uuid)
JSON.lower(id::Identifier{T}) where T <: Model = begin
    properties = LarderORM.uniqueidentifier(T)
    Dict((property => id.values[i] for (i, property) ∈ enumerate(properties))...)
end

whoami(req; context) = req[:jwt_identity].user

struct ListResponse{T}
    values::Vector{T}
end

struct InvalidRequestError <: Exception
    msg::String
end

struct NotAuthorizedError <: Exception
end

listusers(req; context) = begin
    ListResponse(selectmodels(context.db, User))
end

listnamespaces(req; context) = begin
    user = req[:params][:user]
    uuid = tryparse(UUID, user)
    iam = whoami(req; context)
    if !(allowadmindashboard in req[:jwt_identity].permissions) && iam.id != uuid
        throw(NotAuthorizedError())
    end
    if isnothing(uuid)
        throw(InvalidRequestError("Invalid UUID: $user"))
    end
    ListResponse(selectmodels(context.db, UserNamespace, [:id => Identifier{User}(uuid)]))
end

addnamespace(req; context) = begin
    namespace = req[:params][:namespace]
    uuid = tryparse(UUID, req[:params][:user])
    if !(allowadmindashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if isnothing(uuid)
        throw(InvalidRequestError("Invalid UUID: $user"))
    end
    user = Identifier{User}(uuid)
    transact(context.db) do db
        existing = selectmodel(db, Identifier(UserNamespace(user, namespace)))
        if isnothing(existing)
            insertmodel!(db, UserNamespace(user, namespace))
        end
    end
    return Dict()
end

removenamespace(req; context) = begin
    namespace = req[:params][:namespace]
    uuid = tryparse(UUID, req[:params][:user])
    if !(allowadmindashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if isnothing(uuid)
        throw(InvalidRequestError("Invalid UUID: $user"))
    end
    user = Identifier{User}(uuid)
    deletemodel!(context.db, Identifier(UserNamespace(user, namespace)))
    return Dict()
end

const migrations = Migrations(
    1 => Migration(
        db -> execute(db, """
        CREATE TABLE IF NOT EXISTS users (
            email varchar NOT NULL,
            id uuid NOT NULL,
            PRIMARY KEY (id)
        );

        CREATE TABLE IF NOT EXISTS usernamespaces (
            id uuid NOT NULL,
            namespace varchar NOT NULL,
            FOREIGN KEY (id) REFERENCES users (id),
            PRIMARY KEY (id, namespace)
        );

        CREATE TABLE IF NOT EXISTS repositories (
            name varchar NOT NULL,
            supportsmavendeploy boolean NOT NULL,
            supportspublishportal boolean NOT NULL,
            expirationdays integer,
            mutable boolean NOT NULL,
            PRIMARY KEY (name)
        );

        """),
        db -> execute(db, """
        DROP TABLE IF EXISTS users;
        DROP TABLE IF EXISTS usernamespaces;
        DROP TABLE IF EXISTS repositories;
        """)
    )
)

function context()
    c = Context(Database(LibPQ.Connection(
        "host=$(ENV["LARDER_DB_HOST"]) port=$(ENV["LARDER_DB_PORT"]) dbname=$(ENV["LARDER_DB_NAME"]) user=$(ENV["LARDER_DB_USER"]) password=$(ENV["LARDER_DB_PASSWORD"])"
    )))
    
    migrate(c.db, migrations, lastversion(migrations))
    return c
end

end