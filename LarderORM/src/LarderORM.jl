module LarderORM

import LibPQ
using StructTypes
using Tables

export Model, Database, Identifier, Representation, constraints, uniqueidentifier

abstract type Database end

struct PostgresDatabase <: Database
    conn::LibPQ.Connection
end

Base.close(db::PostgresDatabase) = close(db.conn)

Database(conn::LibPQ.Connection) = PostgresDatabase(conn)

abstract type Model end

struct Representation
    type::String
    nullable::Bool
end

abstract type Identifier{T <: Model} end

struct ConcreteIdentifier{T <: Model} <: Identifier{T}
    values::Tuple
end

function uniqueidentifier(::Type{T}) where T <: Model
    idprop = StructTypes.idproperty(T)
    idprop == :_ ? fieldnames(T) : (idprop,)
end

function Identifier{T}(t::T) where T <: Model
    properties = uniqueidentifier(T)
    ConcreteIdentifier{T}(tuple((t.:($property) for property ∈ properties)...))
end

function Identifier{T}(values...) where T <: Model
    targettype = Tuple{(map(uniqueidentifier(T)) do prop fieldtype(T, prop) end)...}
    ConcreteIdentifier{T}(convert(targettype, values))
end

Identifier(t::T) where T <: Model = Identifier{T}(t)

include("types.jl")

constraints(::Type{T}, columns) where T = []

export Migration, Migrations, lastversion, tablename, migrate, execute, transact, selectmodels, selectmodel, updatemodel!, deletemodel!, insertmodel!, schema

struct Migration
    up::Function
    down::Function
end

function execute(db::PostgresDatabase, query::String, args...; kwargs...)
    counter = 0
    querytransformed = replace(query, '?' => _ -> begin
        counter += 1
        "\$$counter"
    end)
    LibPQ.execute(db.conn, querytransformed, args...; throw_error = true, kwargs...)
end

function transact(f::Function, db::PostgresDatabase)
    execute(db, "BEGIN;")
    try
        out = f(db)
        execute(db, "COMMIT;")
        return out
    catch e
        execute(db, "ROLLBACK;")
        rethrow(e)
    end
end

struct Migrations
    migrations::Vector{Migration}
    function Migrations(migrations::Pair{<:Integer, Migration}...)::Migrations
        sorted_migrations = sort(migrations, by = x -> x.first)
        for (i, m) in enumerate(sorted_migrations)
            if m.first != i
                throw(ArgumentError("Migrations must be sequentially numbered starting from 1; missing migration $(i)"))
            end
        end
        return new([m.second for m in sorted_migrations])
    end
end

function lastversion(migrations::Migrations)
    length(migrations.migrations)
end

function migrate(db::PostgresDatabase, migrations::Migrations, version)
    if version < 0 || version > length(migrations.migrations)
        throw(ArgumentError("Version $version is out of range (0 to $(length(migrations.migrations)))"))
    end
    transact(db) do db
        execute(db, """
        CREATE TABLE IF NOT EXISTS lardermigrations (
            version INTEGER PRIMARY KEY
        );
        """)
        currentversion = rowtable(execute(db, "SELECT MAX(version) FROM lardermigrations;"))[1][:max]
        if ismissing(currentversion)
            currentversion = 0
        end
        if version > currentversion
            for v in (currentversion + 1):version
                migration = migrations.migrations[v]
                migration.up(db)
                execute(db, "INSERT INTO lardermigrations (version) VALUES (?);", (v,))
            end
        elseif version < currentversion
            for v in currentversion:-1:(version + 1)
                migration = migrations.migrations[v]
                migration.down(db)
                execute(db, "DELETE FROM lardermigrations WHERE version = ?;", (v,))
            end
        end
    end
end

function tablename(::Type{T}) where T <: Model
    lowercase(string(Symbol(T)))
end

function dbcolnames(f, t)
    if length(representation(t)) == 1
        ["$f"]
    else
        ["$(f)_$i" for i ∈ 1:length(representation(t))]
    end
end

function createschemarows(f, t)
    rs = representation(t)
    join(["    $(n) $(r.type)$(r.nullable ? "" : " NOT NULL")" for (n, r) ∈ zip(dbcolnames(f, t), rs)], ",\n")
end

fieldsbytype(::Type{T}) where {T <: Model} = [
    (f, fieldtype(T, f)) for f ∈ fieldnames(T)
]

constraints(::Type{Identifier{T}}, columns) where T <: Model = begin
    [
        "FOREIGN KEY ($(join(columns, ", "))) REFERENCES $(tablename(T)) ($(join(
            (join(dbcolnames(property, fieldtype(T, property)), ", ") for property ∈ uniqueidentifier(T)), ", "
        )))"
    ]
end
constraints(::Type{T}, columns) where T <: Tuple = begin
    rs = fieldtypes(T)
    lengths = (length(representation(r)) for r ∈ rs)
    i = 1
    cons = []
    for (r, l) ∈ zip(rs, lengths)
        cs = constraints(r, columns[i:(i + l - 1)])
        append!(cons, cs)
        i += l
    end
    cons
end

function schema(::Type{T}) where {T <: Model}
    """
    CREATE TABLE IF NOT EXISTS $(tablename(T)) (
    $(begin
        entries = [(begin
            createschemarows(f, r)
        end for (f, r) in fieldsbytype(T))..., vcat((begin
            cols = dbcolnames(f, r)
            map(constraints(r, cols)) do l "    $l" end
        end for (f, r) in fieldsbytype(T))...)...]
        idprops = uniqueidentifier(T)
        if !isempty(idprops)
            cols = (join(dbcolnames(prop, fieldtype(T, prop)), ", ") for prop ∈ idprops)
            anynullable = any(r -> any(rep -> rep.nullable, representation(fieldtype(T, r))), idprops)
            push!(entries, "    $(anynullable ? "UNIQUE" : "PRIMARY KEY") ($(join(cols, ", ")))")
        end
        join(entries, ",\n")
    end)
    );
    """
end

function translaterows(results, ::Type{T}) where T <: Model
    decodedtype = Tuple{(fieldtype(T, f) for f ∈ fieldnames(T))...}
    names = fieldnames(T)
    rows = rowtable(results)
    map(rows) do row
        T(decode(tuple(row[names]...), decodedtype)...)
    end
end

function selectmodels(db::PostgresDatabase, ::Type{T}, match::Vector{S} where S <: Pair{Symbol}) where T <: Model
    colnames = Iterators.flatten(dbcolnames(f, r) for (f, r) in fieldsbytype(T))
    whereclause = join(["$(dbcolnames(p[1], fieldtype(T, p[1]))[1]) = ?" for p ∈ match], " AND ")
    query = "SELECT $(join(colnames, ", ")) FROM $(tablename(T)) WHERE $whereclause;"
    args = tuple(Iterators.flatten(encode(p[2], fieldtype(T, p[1])) for p ∈ match)...)
    results = execute(db, query, args)
    translaterows(results, T)
end

function selectmodels(db::PostgresDatabase, ::Type{T}) where T <: Model
    colnames = Iterators.flatten(dbcolnames(f, r) for (f, r) in fieldsbytype(T))
    query = "SELECT $(join(colnames, ", ")) FROM $(tablename(T));"
    results = execute(db, query)
    translaterows(results, T)
end

function selectmodel(db::PostgresDatabase, id::Identifier{T}) where T <: Model
    idprops = uniqueidentifier(T)
    colnames = tuple(Iterators.flatten(dbcolnames(prop, fieldtype(T, prop)) for prop ∈ idprops)...)
    whereclause = join(map(colnames) do name "$name = ?" end, " AND ")
    query = "SELECT * FROM $(tablename(T)) WHERE $whereclause;"
    results = execute(db, query, encode(id, Identifier{T}))
    rows = translaterows(results, T)
    length(rows) == 0 ? nothing : rows[1]
end

function updatemodel!(db::PostgresDatabase, value::T) where T <: Model
    idprops = uniqueidentifier(T)
    idcols = Iterators.flatten(dbcolnames(prop, fieldtype(T, prop)) for prop ∈ idprops)
    whereclause = join(map(idcols) do name "$name = ?" end, " AND ")
    setclauses = []
    args = []
    for (f, r) in fieldsbytype(T)
        if f ∈ idprops
            continue
        end
        cols = dbcolnames(f, r)
        append!(setclauses, map(cols) do name "$name = ?" end)
        append!(args, encode(value.:($f), r))
    end
    query = "UPDATE $(tablename(T)) SET $(join(setclauses, ", ")) WHERE $whereclause;"
    append!(args, encode(Identifier(value), Identifier{T}))
    execute(db, query, args)
end

function deletemodel!(db::PostgresDatabase, value::T) where T <: Model
    colnames = Iterators.flatten(dbcolnames(f, r) for (f, r) in fieldsbytype(T))
    whereclause = join(map(colnames) do name "$name = ?" end, " AND ")
    query = "DELETE FROM $(tablename(T)) WHERE $whereclause;"
    args = tuple(Iterators.flatten(encode(value.:($f), r) for (f, r) in fieldsbytype(T))...)
    execute(db, query, args)
end

function deletemodel!(db::PostgresDatabase, id::Identifier{T}) where T <: Model
    idprops = uniqueidentifier(T)
    idcols = Iterators.flatten(dbcolnames(prop, fieldtype(T, prop)) for prop ∈ idprops)
    whereclause = join(map(idcols) do name "$name = ?" end, " AND ")
    query = "DELETE FROM $(tablename(T)) WHERE $whereclause;"
    execute(db, query, encode(id, Identifier{T}))
end

function insertmodel!(db::PostgresDatabase, value::T) where T <: Model
    colnames = Iterators.flatten(dbcolnames(f, r) for (f, r) in fieldsbytype(T))
    valuesclause = join(["?" for _ in colnames], ", ")
    query = "INSERT INTO $(tablename(T)) ($(join(colnames, ", "))) VALUES ($valuesclause);"
    args = tuple(Iterators.flatten(encode(value.:($f), r) for (f, r) in fieldsbytype(T))...)
    execute(db, query, args)
end

end
