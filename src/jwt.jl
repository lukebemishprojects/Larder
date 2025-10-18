using JWTs
using HTTP
using UUIDs
using Dates

@kwdef struct JwtIdentity
    user::api.User
    permissions::Set{api.Permission}
end

function within(path, req)
    rootparts = splitpath(normpath("/", path))
    pathparts = splitpath(normpath("/", req[:path]...))
    return length(pathparts) >= length(rootparts) && pathparts[1:length(rootparts)] == rootparts
end

function dummyjwt(path; context)
    return (rest, req) -> if within(path, req)
        dummyemail = "xyz@example.org"
        req[:jwt_identity] = JwtIdentity(;
            user = api.newuser(api.User(
                dummyemail,
                uuid5(uuid_iss, dummyemail)
            ), context),
            permissions=Set([api.allowdashboard, api.allowadmindashboard])
        )
        rest(req)
    else rest(req) end
end

const uuid_iss = UUID("f26ee10c-dfd1-4aff-99f2-03140ad59e46")

function jwt(path; context, aud, location, header, permissions)
    keyset = JWKSet(location)
    return (rest, req) -> if (within(path, req))
        if haskey(req, :jwt_identity)
            return rest(req)
        end
        unauthorized = (req) -> mux(status(401), respond("Unauthorized"))(req)
        jwtheader = HTTP.header(req[:headers], header)
        if isempty(jwtheader)
            return unauthorized()
        end
        jwtobj = JWT(; jwt=jwtheader)
        if !validate!(jwtobj, keyset)
            return unauthorized()
        end
        jwtbody = JWTs.decodepart(jwtobj.payload)
        if aud != jwtbody.aud || iss != jwtbody.iss
            return unauthorized()
        end
        if !haskey(jwtbody, :exp) || unix2datetime(jwtbody.exp) < Dates.now(Dates.UTC)
            return unauthorized()
        end
        req[:jwt_identity] = JwtIdentity(;
            user = api.newuser(api.User(
                jwtbody.email,
                uuid5(uuid5(uuid_iss, jwtbody.iss), jwtbody.sub)
            ), context),
            permissions = permissions
        )
        return rest(req)
    else rest(req) end
end