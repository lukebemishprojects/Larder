using UUIDs
using Dates
using LarderORM
using JSON

module schematypes

using UUIDs
using Dates
using LarderORM
using JSON

const schematypes = Symbol[]
macro schema(expr)
    if !(expr isa Expr && expr.head == :struct)
        throw(ArgumentError("@schema macro must be applied to a struct definition"))
    end
    defn = expr.args[2]
    while defn isa Expr
        defn = defn.args[1]
    end
    push!(schematypes, defn)
    return expr
end

@schema struct User <: Model
    email::String
    id::UUID
end

LarderORM.tablename(::Type{User}) = "users"
LarderORM.uniqueidentifier(::Type{User}) = (:id,)

@schema struct UserNamespace <: Model
    id::Identifier{User}
    namespace::String
    confirmed::Bool
end

LarderORM.tablename(::Type{UserNamespace}) = "usernamespaces"
LarderORM.uniqueidentifier(::Type{UserNamespace}) = (:id, :namespace)

export RepositoryBackendType, s3backend
@enum RepositoryBackendType::Int32 s3backend
LarderORM.representation(::Type{RepositoryBackendType}) = LarderORM.representation(Int32)
LarderORM.encode(value::RepositoryBackendType, ::Type{RepositoryBackendType}) = LarderORM.encode(Int32(value), Int32)
LarderORM.decode(value::Tuple{Int32}, ::Type{RepositoryBackendType}) = RepositoryBackendType(value[1])

@schema struct RepositoryBackend <: Model
    id::UUID
    type::RepositoryBackendType
end

LarderORM.tablename(::Type{RepositoryBackend}) = "repositorybackends"
LarderORM.uniqueidentifier(::Type{RepositoryBackend}) = (:id,)

@schema struct S3Backend <: Model
    id::Identifier{RepositoryBackend}
    region::String
    endpoint::String
    accesskeyid::String
    secretaccesskey::String
end

LarderORM.tablename(::Type{S3Backend}) = "s3backends"
LarderORM.uniqueidentifier(::Type{S3Backend}) = (:id,)

@schema struct Repository <: Model
    name::String
    supportsmavendeploy::Bool
    supportspublishportal::Bool
    expirationdays::Int32
    mutable::Bool
    backend::Identifier{RepositoryBackend}
end

LarderORM.tablename(::Type{Repository}) = "repositories"
LarderORM.uniqueidentifier(::Type{Repository}) = (:name, )

@schema struct RepositoryIndex <: Model
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

@schema struct S3BackendConfigurations <: Model
    repository::Identifier{Repository}
    backend::Identifier{S3Backend}
    bucket::String
    prefix::String
end

LarderORM.tablename(::Type{S3BackendConfigurations}) = "s3backendconfigurations"
LarderORM.uniqueidentifier(::Type{S3BackendConfigurations}) = (:repository,)

end

using .schematypes

const schematypes = begin
    bytype = Set([schematypes.:($name) for name ∈ names(schematypes, all=true) if isa(schematypes.:($name), DataType) && schematypes.:($name) <: Model])
    bysymbol = schematypes.schematypes
    typesfrombysymbol = DataType[]
    for sym in bysymbol
        val = schematypes.:($sym)
        if isa(val, DataType) && val <: Model
            push!(typesfrombysymbol, val)
            delete!(bytype, val)
        else
            throw(ArgumentError("Symbol $sym does not correspond to a Model type"))
        end
    end
    if !isempty(bytype)
        throw(ArgumentError("Some Model types were not registered via @schema macro: $(collect(bytype))"))
    end
    typesfrombysymbol
end

for t ∈ schematypes
    @eval const $(nameof(t)) = $t
end

JSON.lower(uuid::UUID) = string(uuid)
JSON.lower(id::Identifier{T}) where T <: Model = begin
    properties = LarderORM.uniqueidentifier(T)
    Dict((property => id.values[i] for (i, property) ∈ enumerate(properties))...)
end
JSON.lower(type::RepositoryBackendType) = string(Symbol(type))

function printschemas()
    for T in schematypes
        println(schema(T))
    end
end