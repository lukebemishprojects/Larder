module App

using Mux
using Larder

appenv = get(ENV, "LARDER_ENV", "prod")

dashboard = if appenv == "dev"
    run(Cmd(`bash generate.sh`, env=("APP_ENV" => appenv, "PATH" => ENV["PATH"])), wait = false)
end

server = serve(Larder.app())

Base.exit_on_sigint(false)
try wait()
catch e
    if isa(e, InterruptException)
        Base.exit_on_sigint(true)
        println("Shutting down...")
        close(server)
    else
        println(e)
        rethrow(e)
    end
end

end