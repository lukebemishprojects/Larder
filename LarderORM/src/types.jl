using Dates
using UUIDs

export representation, encode, decode, VarChar

representation(::Type{T}) where {T} = (Representation("varchar", false),)
representation(::Type{String}) = (Representation("varchar", false),)
representation(::Type{Identifier{T}}) where T <: Model = representation(fieldtype(T, StructTypes.idproperty(T)))
representation(::Type{Union{T, Missing}}) where T = map(representation(T)) do r Representation(r.type, true) end
representation(::Type{Union{T, Nothing}}) where T = map(representation(T)) do r Representation(r.type, true) end
representation(::Type{T}) where T <: Tuple = tuple(Iterators.flatten(representation(t) for t ∈ fieldtypes(T))...)

encode(x::T, ::Type{T}) where T = (string(x),)
encode(x::String, ::Type{String}) = (x,)
encode(x::Identifier{T}, ::Type{Identifier{T}}) where T <: Model = encode(x.value, fieldtype(T, StructTypes.idproperty(T)))
encode(x::T, ::Type{Union{T, Missing}}) where T = encode(x, T)
encode(::Missing, ::Type{Union{T, Missing}}) where T = (missing,)
encode(x::T, ::Type{Union{T, Nothing}}) where T = encode(x, T)
encode(::Nothing, ::Type{Union{T, Nothing}}) where T = (missing,)
@generated encode(x::T, ::Type{T}) where T <: Tuple = begin
    Expr(:tuple, (
        :(encode(x[$i], $(r))...) for (i, r) ∈ enumerate(fieldtypes(T))
    )...)
end

decode(x::Tuple{String}, ::Type{T}) where T = parse(T, x[1])
decode(x::Tuple{String}, ::Type{String}) = x[1]
decode(x::Tuple, ::Type{Identifier{T}}) where T <: Model = ConcreteIdentifier{T}(decode(x, fieldtype(T, StructTypes.idproperty(T))))
decode(::Tuple{Missing}, ::Type{<:Missing}) = missing
decode(::Tuple{Missing}, ::Type{<:Nothing}) = nothing
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

LibPQ.LIBPQ_TYPE_MAP[:uuid] = UUID
