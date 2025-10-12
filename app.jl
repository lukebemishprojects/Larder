module App

using Mux
using Loggerhead

appenv = get(ENV, "APP_ENV", "dev")

dashboard = if appenv == "dev"
    run(Cmd(`bash generate.sh`, env=("APP_ENV" => appenv, "PATH" => ENV["PATH"])), wait = false)
end

wait(serve(Loggerhead.app(appenv)))

end