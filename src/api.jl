module api

using JSON
using Printf
import LibPQ
using UUIDs
using Dates
using LarderORM
using StructTypes
using StructUtils

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
    confirmed::Bool
end

LarderORM.tablename(::Type{UserNamespace}) = "usernamespaces"
LarderORM.uniqueidentifier(::Type{UserNamespace}) = (:id, :namespace)

struct Repository <: Model
    name::String
    supportsmavendeploy::Bool
    supportspublishportal::Bool
    expirationdays::Int32
    mutable::Bool
end

LarderORM.tablename(::Type{Repository}) = "repositories"
StructTypes.idproperty(::Type{Repository}) = :name

struct RepositoryIndex <: Model
    repository::Identifier{Repository}
    path::String
    name::String
    isdirectory::Bool
    lastmodified::Union{DateTime, Nothing}
    size::Union{Int64, Nothing}
    expires::Int64
end

LarderORM.tablename(::Type{RepositoryIndex}) = "repositoryindices"
LarderORM.uniqueidentifier(::Type{RepositoryIndex}) = (:repository, :path, :name)

function printschemas()
    for T in (User, UserNamespace, Repository, RepositoryIndex)
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

struct NotFoundError <: Exception
    msg::String
end

parsebody(data::IO) = data
parsebody(data::Vector{UInt8}) = String(data)
getbody(req) = parsebody(req[:data])

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

isvalidnamespace(namespace::AbstractString) = !isnothing(match(r"^[a-z0-9-]+(\.[a-z0-9-]+)*$", namespace))

addnamespace(req; context) = begin
    namespace = req[:params][:namespace]
    uuid = tryparse(UUID, req[:params][:user])
    if !(allowadmindashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if isnothing(uuid)
        throw(InvalidRequestError("Invalid UUID: $user"))
    end
    if !isvalidnamespace(namespace)
        throw(InvalidRequestError("Invalid namespace name: $namespace"))
    end
    user = Identifier{User}(uuid)
    transact(context.db) do db
        existing = selectmodel(db, Identifier(UserNamespace(user, namespace, true)))
        if isnothing(existing)
            insertmodel!(db, UserNamespace(user, namespace, true))
        end
    end
    return Dict()
end

requestnamespace(req; context) = begin
    namespace = req[:params][:namespace]
    uuid = tryparse(UUID, req[:params][:user])
    iam = whoami(req; context)
    if isnothing(uuid)
        throw(InvalidRequestError("Invalid UUID: $user"))
    end
    if iam.id != uuid
        throw(NotAuthorizedError())
    end
    if !isvalidnamespace(namespace)
        throw(InvalidRequestError("Invalid namespace name: $namespace"))
    end
    user = Identifier{User}(uuid)
    transact(context.db) do db
        existing = selectmodel(db, Identifier(UserNamespace(user, namespace, false)))
        if isnothing(existing)
            insertmodel!(db, UserNamespace(user, namespace, false))
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
    deletemodel!(context.db, Identifier(UserNamespace(user, namespace, false)))
    return Dict()
end

confirmnamespace(req; context) = begin
    namespace = req[:params][:namespace]
    uuid = tryparse(UUID, req[:params][:user])
    if !(allowadmindashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if isnothing(uuid)
        throw(InvalidRequestError("Invalid UUID: $user"))
    end
    user = Identifier{User}(uuid)
    ns = selectmodel(context.db, Identifier(UserNamespace(user, namespace, false)))
    if isnothing(ns)
        throw(NotFoundError("Namespace not found"))
    end
    ns = ns::UserNamespace
    ns = UserNamespace(ns.id, ns.namespace, true)
    updatemodel!(context.db, ns)
    return Dict()
end

const reservedpaths = Set(["api", "dashboard", "publish", "_internal"])

isvalidrepositoryname(name::AbstractString) = begin
    !isnothing(match(r"^[a-z0-9._-]+$", name)) && !(name in reservedpaths)
end

updaterepository(req; context) = begin
    repositoryname = req[:params][:repositoryname]
    if !(allowadmindashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if !isvalidrepositoryname(repositoryname)
        throw(InvalidRequestError("Invalid repository name: $repositoryname"))
    end
    json = nothing
    try
        json = JSON.parse(getbody(req))
    catch _
        throw(InvalidRequestError("Invalid JSON body"))
    end
    if "name" in keys(json) && json["name"] != repositoryname
        throw(InvalidRequestError("Repository name in URL and body do not match"))
    end
    json["name"] = repositoryname
    repo = StructUtils.make(Repository, json)
    if repo.expirationdays < 0
        throw(InvalidRequestError("Expiration days must be non-negative"))
    end
    transact(context.db) do db
        existing = selectmodel(db, Identifier(repo))
        if isnothing(existing)
            insertmodel!(db, repo)
        elseif existing != repo
            updatemodel!(db, repo)
        end
    end
    return Dict()
end

removerepository(req; context) = begin
    repositoryname = req[:params][:repositoryname]
    if !(allowadmindashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if !isvalidrepositoryname(repositoryname)
        throw(InvalidRequestError("Invalid repository name: $repositoryname"))
    end
    deletemodel!(context.db, Identifier{Repository}(repositoryname))
    return Dict()
end

getrepository(req; context) = begin
    repositoryname = req[:params][:repositoryname]
    if !(allowdashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    if !isvalidrepositoryname(repositoryname)
        throw(InvalidRequestError("Invalid repository name: $repositoryname"))
    end
    repo = selectmodel(context.db, Identifier{Repository}(repositoryname))
    if isnothing(repo)
        throw(NotFoundError("Repository not found"))
    end
    return repo
end

listrepositories(req; context) = begin
    if !(allowdashboard in req[:jwt_identity].permissions)
        throw(NotAuthorizedError())
    end
    ListResponse(selectmodels(context.db, Repository))
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
            confirmed boolean NOT NULL,
            FOREIGN KEY (id) REFERENCES users (id),
            PRIMARY KEY (id, namespace)
        );

        CREATE TABLE IF NOT EXISTS repositories (
            name varchar NOT NULL,
            supportsmavendeploy boolean NOT NULL,
            supportspublishportal boolean NOT NULL,
            expirationdays integer NOT NULL,
            mutable boolean NOT NULL,
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


        """),
        db -> execute(db, """
        DROP TABLE IF EXISTS users;
        DROP TABLE IF EXISTS usernamespaces;
        DROP TABLE IF EXISTS repositories;
        DROP TABLE IF EXISTS repositoryindices;
        """)
    )
)

macro templatefile_str(filename)
    contents = open(filename) do f
        read(f, String)
    end
    expr = Meta.parse("\"\"\"$contents\"\"\"")
    return esc(expr)
end

struct IndexEntry
    name::String
    datetime::String
    size::String
end

function fillindex(path::String, entries::Vector{IndexEntry})
    templatefile"indices/dist/index-page.html"
end

humansize(bytes) = begin
    threshold = 1000

    if abs(bytes) < threshold
        return string(bytes)
    end

    ofunit = Float64(bytes)
	units = ['K', 'M', 'G', 'T', 'P', 'E', 'Z', 'Y']
	u = 0
	r = 10

    while true
        ofunit /= threshold
        u++
        if round(ofunit, digits=1) < threshold || u == length(units)
            break
        end
    end

    "$(round(ofunit, digits=1))$(units[u])"
end

indexresponse(method, html) = method != "HEAD" ? Dict{Symbol, Any}(
    :body => html,
    :headers => Dict("Content-Type" => "text/html")
) : Dict{Symbol, Any}(
    :headers => Dict("Content-Type" => "text/html")
)

showrepositories(req; context) = begin
    repos = selectmodels(context.db, Repository)
    return indexresponse(req[:method], fillindex("/", [IndexEntry(
        "$(repo.name)/",
        "",
        ""
    ) for repo in sort(repos, by = x -> x.name)]))
end

showindex(req, rest, repositoryname, path; context) = begin
    parent = Identifier{RepositoryIndex}(
        Identifier{Repository}(repositoryname),
        path,
        "../"
    )
    index = selectmodel(context.db, parent)
    if isnothing(index)
        # TODO: index page if not present and fall back to rest if necessary
        return rest(req)
    end
    matching = selectmodels(context.db, RepositoryIndex, [
        :repository => Identifier{Repository}(repositoryname),
        :path => path
    ])
    entries = [IndexEntry(
        "../",
        "",
        ""
    )]
    matching = sort(matching, by = x -> (x.isdirectory ? 0 : 1, x.name))
    for entry in matching
        if (entry.name == "../")
            continue
        end
        datetime = entry.isdirectory ? "" : Dates.format(entry.lastmodified, dateformat"yyyy-mm-ddTHH:MM:SS.s")
        size = entry.isdirectory ? "" : humansize(entry.size)
        push!(entries, IndexEntry(
            entry.name,
            datetime,
            size
        ))
    end
    return indexresponse(req[:method], fillindex("/"+path, entries))
end

function context()
    c = Context(Database(LibPQ.Connection(
        "host=$(ENV["LARDER_DB_HOST"]) port=$(ENV["LARDER_DB_PORT"]) dbname=$(ENV["LARDER_DB_NAME"]) user=$(ENV["LARDER_DB_USER"]) password=$(ENV["LARDER_DB_PASSWORD"])"
    )))
    
    migrate(c.db, migrations, lastversion(migrations))
    return c
end

end