module api

using JSON
using UUIDs
import LibPQ
using LarderORM
using StructTypes

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

JSON.lower(u::User) = Dict("email" => u.email, "id" => string(u.id))

whoami(req; context) = req[:jwt_identity].user

const migrations = Migrations(
    1 => Migration(
        db -> execute(db, """
        CREATE TABLE IF NOT EXISTS users (
            email varchar NOT NULL,
            id uuid NOT NULL,
            UNIQUE (id)
        );
        """),
        db -> execute(db, """
        DROP TABLE IF EXISTS users;
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