module api

#=SearchLight.connect(Dict{String, Union{String, Nothing}}([
    "adapter" => "PostgreSQL",
    "host" => ENV["LARDER_DB_HOST"],
    "port" => ENV["LARDER_DB_PORT"],
    "database" => ENV["LARDER_DB_NAME"],
    "username" => ENV["LARDER_DB_USER"],
    "password" => ENV["LARDER_DB_PASSWORD"]
]))
SearchLight.Migrations.init()
SearchLight.Migrations.last_up()=#
#TODO: figure out what to use instead of searchlight since it had literally no docs and is closely tied to genie

#TODO: use OIDC/ZeroTrust sub here for ID
struct User
    email::String
end

whoami(req) = User(req[:jwt_identity].email)

end