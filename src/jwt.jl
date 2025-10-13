using JWTs
using HTTP

@enum Permission allowdashboard allowadmindashboard

struct JwtIdentity
    email::String
    # TODO: use "sub" field of JWT for identity instead of email?
    permissions::Set{Permission}
end

function within(path, req)
    rootparts = splitpath(normpath("/", path))
    pathparts = splitpath(normpath("/", req[:path]...))
    return length(pathparts) >= length(rootparts) && pathparts[1:length(rootparts)] == rootparts
end

function dummyjwt(path)
    return (rest, req) -> if (within(path, req))
        req[:jwt_identity] = JwtIdentity("xyz@example.org", Set([allowdashboard, allowadmindashboard]))
        rest(req)
    else rest(req) end
end

function jwt(path; aud, iss, location, header, permissions)
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
        # TODO: Check exp time
        req[:jwt_identity] = JwtIdentity(jwtbody.email, permissions)
        return rest(req)
    else rest(req) end
end