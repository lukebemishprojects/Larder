module Larder

using Mux
using JSON

include("api.jl")
include("jwt.jl")

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

fileresponse(method, f) = method != "HEAD" ? Dict{Symbol, Any}(
    :body => read(f),
    :headers => fileheaders(f)
) : Dict{Symbol, Any}(
    :headers => fileheaders(f)
)

jsonresponse(method, obj) = method != "HEAD" ? Dict{Symbol, Any}(
    :body => JSON.json(obj),
    :headers => Dict("Content-Type" => "application/json")
) : Dict{Symbol, Any}(
    :headers => Dict("Content-Type" => "application/json")
)

function files(root, from)
    branch(
        req -> !isnothing(validpath(root, joinpath(req[:path]...), from)),
        req -> fileresponse(req[:method], validpath(root, joinpath(req[:path]...), from))
    )
end

function (@main)(args:: Vector{String}=ARGS)
    apicontext = api.context()
    server = serve(Larder.app(apicontext); on_shutdown=()->close(apicontext))

    toclose = Ref{Any}(server)
    atexit() do
        closable = toclose[]
        if !isnothing(closable)
            close(closable)
        end
    end

    try
        wait(server)
    finally
        close(server)
    end
end

formethod(method::String, callback) = (rest, req) -> req[:method] == method ? callback(rest, req) : rest(req)
formethods(methods::Vector{String}, callback) = (rest, req) -> in(req[:method], methods) ? callback(rest, req) : rest(req)

function app(apicontext)
    appenv = get(ENV, "LARDER_ENV", "prod")
    dashboardaud = get(ENV, "LARDER_JWT_AUD", nothing)
    admindashboardaud = get(ENV, "LARDER_ADMIN_JWT_AUD", nothing)
    jwtcertificatelocation = get(ENV, "LARDER_JWT_CERT_LOCATION", nothing)
    jwtheader = get(ENV, "LARDER_JWT_HEADER", nothing)
    signout = get(ENV, "LARDER_SIGNOUT_URL", nothing)

    if appenv != "dev" && any(isnothing, [dashboardaud, admindashboardaud, jwtcertificatelocation, jwtheader, signout])
        error("In production, JWT header validation must be set up for the dashboard and admin dashboard to determine identity!")
    end

    function apipage(path, handler)
        return page(path, req -> try
            val = handler(req; context=apicontext)
            jsonresponse(req[:method], val)
        catch e
            if e isa api.InvalidRequestError
                mux(Mux.status(400), req -> jsonresponse("GET", Dict("error" => e.msg)))(req)
            elseif e isa api.NotAuthorizedError
                mux(Mux.status(401), respond("Unauthorized"))(req)
            elseif e isa api.NotFoundError
                mux(Mux.status(404), req -> jsonresponse("GET", Dict("error" => e.msg)))(req)
            else
                rethrow(e)
            end
        end)
    end

    function showindex()
        return (rest, req) -> begin
            parts = req[:path]
            if length(parts) == 0
                return api.showrepositories(req; context=apicontext)
            end
            if !endswith(req[:uri].path, '/')
                # TODO: do we need this? We could also just ask for the S3/whatever _first_ and then this would just work out of the box.
                return rest(req)
            end
            path = join(parts[2:end], "/")
            repositoryname = parts[1]
            return api.showindex(req, rest, repositoryname, path; context=apicontext)
        end
    end

    @app app = (
        appenv == "dev" ? Mux.defaults : Mux.prod_defaults,
        appenv == "dev" ? dummyjwt("dashboard/admin/"; context=apicontext) : jwt("dashboard/admin/"; aud = admindashboardaud, location = jwtcertificatelocation, header = jwtheader, permissions = Set([api.allowadmindashboard, api.allowdashboard]), context=apicontext),
        appenv == "dev" ? dummyjwt("dashboard/"; context=apicontext) : jwt("dashboard/"; aud = dashboardaud, location = jwtcertificatelocation, header = jwtheader, permissions = Set([api.allowdashboard]), context=apicontext),
        formethods(["GET", "HEAD"], Mux.stack(
            apipage("dashboard/admin/api/listusers", api.listusers),
            apipage("dashboard/admin/api/repositories", api.listrepositories),
            apipage("dashboard/admin/api/repositories/:repositoryname", api.getrepository),
            apipage("dashboard/admin/api/backends", api.listbackends),
            apipage("dashboard/admin/api/backends/:id", api.getbackend),

            apipage("dashboard/api/whoami", api.whoami),
            apipage("dashboard/api/namespaces/:user/list", api.listnamespaces),

            page("dashboard/logout/", isnothing(signout) ? Mux.notfound("Signing out doesn't make sense in dev!") : respond(Dict(
                :status => 302,
                :headers => [("Location", signout)]
            ))),

            route("dashboard/api", Mux.notfound()),
            route("dashboard/admin/api", Mux.notfound()),
            files("dashboard/", "dashboard/dist/"),
            files("_internal/", "indices/dist/_internal/"),
            showindex()
        )),
        formethod("POST", Mux.stack(
            apipage("dashboard/admin/api/namespaces/:user/create/:namespace", api.addnamespace),
            apipage("dashboard/admin/api/namespaces/:user/confirm/:namespace", api.confirmnamespace),
            apipage("dashboard/admin/api/namespaces/:user/delete/:namespace", api.removenamespace),
            apipage("dashboard/admin/api/repositories/:repositoryname", api.updaterepository),
            apipage("dashboard/admin/api/backends/:id", api.updatebackend),
            apipage("dashboard/admin/api/backends", api.createbackend),

            apipage("dashboard/api/namespaces/:user/request/:namespace", api.requestnamespace),
        )),
        formethod("DELETE", Mux.stack(
            apipage("dashboard/admin/api/repositories/:repositoryname", api.removerepository),
            apipage("dashboard/admin/api/backends/:id", api.removebackend)
        )),
        Mux.notfound()
    )
    return app
end

public app

end