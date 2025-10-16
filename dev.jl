Base.exit_on_sigint(false)

using Pkg
Pkg.activate(@__DIR__)

using Larder
using Mux

ENV["LARDER_ENV"] = "dev"
ENV["LARDER_DB_HOST"] = "localhost"
ENV["LARDER_DB_PORT"] = "5432"
ENV["LARDER_DB_NAME"] = "larder"
ENV["LARDER_DB_USER"] = "larder"
ENV["LARDER_DB_PASSWORD"] = "larder"

run(`docker compose -f docker-compose.dev.yml up --remove-orphans --detach`)
dashboard = run(pipeline(Cmd(`bash generate.sh`, env=("LARDER_ENV" => ENV["LARDER_ENV"], "PATH" => ENV["PATH"]), dir = joinpath(@__DIR__)), stdout = Core.stdout, stderr = Core.stderr), wait = false)

atexit() do
    run(`docker compose -f docker-compose.dev.yml down`)
end

atexit() do
    kill(dashboard)
end

Larder.main(ARGS)
