module Larder

using Mux
using JSON

include("jwt.jl")
include("api.jl")

mimetypes = Dict(
    ".json" => "application/json",
    ".html" => "text/html",
    ".css" => "text/css",
    ".js" => "application/javascript",
    ".svg" => "image/svg+xml"
)

function validpath(root, path, from)
    path = normpath("/", path)
    from = normpath(from)
    root = normpath("/", root)
    parts = splitpath(path)
    rootparts = splitpath(root)
    if length(parts) < length(rootparts)
        return nothing
    end
    if parts[1:length(rootparts)] == rootparts
        remainder = joinpath(from, parts[length(rootparts)+1:end]...)
        if isfile(remainder)
            return remainder
        elseif isdir(remainder) && isfile(joinpath(remainder, "index.html"))
            return joinpath(remainder, "index.html")
        end
        return nothing
    end
end

fileheaders(f) = Dict("Content-Type" => get(mimetypes, splitext(f)[2], "application/octet-stream"))

fileresponse(f) = Dict(
    :body => read(f),
    :headers => fileheaders(f)
)

jsonresponse(obj) = Dict(
    :body => JSON.json(obj),
    :headers => Dict("Content-Type" => "application/json")
)

function files(root, from)
    branch(
        req -> !isnothing(validpath(root, joinpath(req[:path]...), from)),
        req -> fresp(validpath(root, joinpath(req[:path]...), from))
    )
end

fresp(f) =
    endswith(f, "/") ? fileresponse(joinpath(f, "index.html")) : fileresponse(f)

function app()
    appenv = get(ENV, "LARDER_ENV", "prod")
    dashboardaud = get(ENV, "LARDER_JWT_AUD", nothing)
    admindashboardaud = get(ENV, "LARDER_ADMIN_JWT_AUD", nothing)
    jwtcertificateissuer = get(ENV, "LARDER_JWT_ISSUER", nothing)
    jwtcertificatelocation = get(ENV, "LARDER_JWT_CERT_LOCATION", nothing)
    jwtheader = get(ENV, "LARDER_JWT_HEADER", nothing)
    signout = get(ENV, "LARDER_SIGNOUT_URL", nothing)

    if appenv != "dev" && any(isnothing, [dashboardaud, admindashboardaud, jwtcertificateissuer, jwtcertificatelocation, jwtheader, signout])
        error("In production, JWT header validation must be set up for the dashboard and admin dashboard to determine identity!")
    end

    function apipage(path, handler)
        return page(path, req -> jsonresponse(handler(req)))
    end

    @app app = (
        appenv == "dev" ? Mux.defaults : Mux.prod_defaults,
        appenv == "dev" ? dummyjwt("dashboard/admin/") : jwt("dashboard/admin/"; aud = admindashboardaud, iss = jwtcertificateissuer, location = jwtcertificatelocation, header = jwtheader, permissions = Set([allowadmindashboard, allowdashboard])),
        appenv == "dev" ? dummyjwt("dashboard/") : jwt("dashboard/"; aud = dashboardaud, iss = jwtcertificateissuer, location = jwtcertificatelocation, header = jwtheader, permissions = Set([allowdashboard])),
        apipage("dashboard/api/whoami", api.whoami),
        route("dashboard/api", Mux.notfound()),
        page("dashboard/logout/", isnothing(signout) ? Mux.notfound("Signing out doesn't make sense in dev!") : respond(Dict(
            :status => 302,
            :headers => [("Location", signout)]
        ))),
        files("dashboard/", "dashboard/dist/"),
        Mux.notfound()
    )
    return app
end

public app

end