Base.exit_on_sigint(false)

using Pkg
Pkg.activate(@__DIR__)

ENV["LARDER_ENV"] = "dev"
ENV["LARDER_DB_HOST"] = "localhost"
ENV["LARDER_DB_PORT"] = "5432"
ENV["LARDER_DB_NAME"] = "larder"
ENV["LARDER_DB_USER"] = "larder"
ENV["LARDER_DB_PASSWORD"] = "larder"

ENV["LARDER_S3_REGION"] = ""
ENV["LARDER_S3_ACCESS_KEY_ID"] = "minioadmin"
ENV["LARDER_S3_SECRET_ACCESS_KEY"] = "minioadmin"
ENV["LARDER_S3_ENDPOINT"] = "http://localhost:9000"

run(`docker compose -f docker-compose.dev.yml up --remove-orphans --detach`)
dashboard = run(pipeline(Cmd(`bash generate.sh`, env=("LARDER_ENV" => ENV["LARDER_ENV"], "PATH" => ENV["PATH"]), dir = joinpath(@__DIR__)), stdout = Core.stdout, stderr = Core.stderr), wait = false)

atexit() do
    run(`docker compose -f docker-compose.dev.yml down`)
end

atexit() do
    kill(dashboard)
end

using Larder

Larder.main(ARGS)
