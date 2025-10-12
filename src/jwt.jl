using JWTs
using HTTP

struct JwtIdentity
    email::String
end

function within(path, req)
    rootparts = splitpath(normpath("/", path))
    pathparts = splitpath(normpath("/", req[:path]...))
    return length(pathparts) >= length(rootparts) && pathparts[1:length(rootparts)] == rootparts
end

function dummyjwt(path)
    return (rest, req) -> if (within(path, req))
        req[:jwt_identity] = JwtIdentity("xyz@example.org")
        rest(req)
    else rest(req) end
end

function jwt(path; aud, iss, location, header)
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
        req[:jwt_identity] = JwtIdentity(jwtbody.email)
        return rest(req)
    else rest(req) end
end