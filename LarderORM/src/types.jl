using Dates
using UUIDs

export representation, encode, decode, VarChar, StructuredColumns

representation(::Type{T}) where {T} = (Representation("varchar", false),)
representation(::Type{String}) = (Representation("varchar", false),)
representation(::Type{Identifier{T}}) where T <: Model = tuple(Iterators.flatten(representation(fieldtype(T, property)) for property ∈ uniqueidentifier(T))...)
representation(::Type{Union{T, Missing}}) where T = map(representation(T)) do r Representation(r.type, true) end
representation(::Type{Union{T, Nothing}}) where T = map(representation(T)) do r Representation(r.type, true) end
representation(::Type{T}) where T <: Tuple = tuple(Iterators.flatten(representation(t) for t ∈ fieldtypes(T))...)

encode(x::T, ::Type{T}) where T = (string(x),)
encode(x::String, ::Type{String}) = (x,)
encode(x::Identifier{T}, ::Type{Identifier{T}}) where T <: Model = begin
    identifiertype = Tuple{(fieldtype(T, property) for property ∈ uniqueidentifier(T))...}
    encode(x.values, identifiertype)
end
encode(x::T, ::Type{Union{T, Missing}}) where T = encode(x, T)
encode(::Missing, ::Type{Union{T, Missing}}) where T = ntuple(i->missing, length(representation(T)))
encode(x::T, ::Type{Union{T, Nothing}}) where T = encode(x, T)
encode(::Nothing, ::Type{Union{T, Nothing}}) where T = ntuple(i->missing, length(representation(T)))
@generated encode(x::T, ::Type{T}) where T <: Tuple = begin
    Expr(:tuple, (
        :(encode(x[$i], $(r))...) for (i, r) ∈ enumerate(fieldtypes(T))
    )...)
end

decode(x::Tuple, ::Type{T}) where T = parse(T, x[1])
decode(x::Tuple{String}, ::Type{String}) = x[1]
decode(x::Tuple, ::Type{Identifier{T}}) where T <: Model = begin
    identifiertype = Tuple{(fieldtype(T, property) for property ∈ uniqueidentifier(T))...}
    ConcreteIdentifier{T}(decode(x, identifiertype))
end
decode(::Tuple{Vararg{Missing}}, ::Type{Union{T, Missing}}) where T = missing
decode(::Tuple{Vararg{Missing}}, ::Type{Union{T, Nothing}}) where T = nothing
decode(x::Tuple{String}, ::Type{Union{T, Missing}}) where T = decode(x, T)
decode(x::Tuple{String}, ::Type{Union{T, Nothing}}) where T = decode(x, T)
decode(x::Tuple, ::Type{T}) where T <: Tuple = begin
    rs = fieldtypes(T)
    lengths = (length(representation(r)) for r ∈ rs)
    starts = (1, (cumsum(lengths) .+ 1)...)
    ranges = ((s:(e-1) for (s, e) ∈ zip(starts, starts[2:end]))...,)
    tuple((decode(x[range], r) for (range, r) ∈ zip(ranges, rs))...)
end

struct VarChar{N}
    value::String
    VarChar{N}(s::String) where {N} = begin
        @assert N isa Integer && N > 0
        if length(s) > N
            throw(ArgumentError("String length $(length(s)) exceeds maximum length of $N"))
        end
        new{N}(s)
    end
end
Base.string(v::VarChar) = v.value
Base.parse(::Type{VarChar{N}}, s::String) where {N} = VarChar{N}(s)
representation(::Type{VarChar{N}}) where {N} = (Representation("varchar($N)", false),)

abstract type StructuredColumns end
representation(::Type{S}) where S <: StructuredColumns = tuple(Iterators.flatten(representation(t) for t ∈ fieldtypes(S))...)
encode(x::S, ::Type{S}) where S <: StructuredColumns = begin
    delegatetype = Tuple{fieldtypes(S)...}
    values = tuple(x.:($f) for f ∈ fieldnames(S))
    encode(values, delegatetype)
end
decode(x::Tuple, ::Type{S}) where S <: StructuredColumns = begin
    delegatetype = Tuple{fieldtypes(S)...}
    values = decode(x, delegatetype)
    S(values...)
end

macro directlyrepresented(r, T)
    quote
        (::typeof($representation))(::Type{$T}) = (Representation($r, false),)
        (::typeof($encode))(x::$T, ::Type{$T}) = (x,)
        (::typeof($decode))(x::Tuple{$T}, ::Type{$T})::$T = x[1]
    end
end

@directlyrepresented "smallint" Int16
@directlyrepresented "integer" Int32
@directlyrepresented "bigint" Int64
@directlyrepresented "real" Float32
@directlyrepresented "double precision" Float64
@directlyrepresented "bytea" Vector{UInt8}
@directlyrepresented "boolean" Bool
@directlyrepresented "timestamp" DateTime
@directlyrepresented "date" Date
@directlyrepresented "time" Time
@directlyrepresented "uuid" UUID
