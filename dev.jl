ENV["LARDER_ENV"] = "dev"
ENV["LARDER_DB_HOST"] = "localhost"
ENV["LARDER_DB_PORT"] = "5432"
ENV["LARDER_DB_NAME"] = "larder"
ENV["LARDER_DB_USER"] = "larder"
ENV["LARDER_DB_PASSWORD"] = "larder"

run(`docker compose -f docker-compose.dev.yml up --remove-orphans --detach`)

try
    include("app.jl")
finally
    run(`docker compose -f docker-compose.dev.yml down`)
end
