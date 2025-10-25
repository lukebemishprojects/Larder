FROM julia:1.12.0-trixie

ENV APP_ENV="prod"

RUN useradd --create-home --shell /bin/bash app

# setting up the app's directory
RUN mkdir /home/app/${APP} && chown app:app /home/app/${APP}
WORKDIR /home/app/${APP}

ENV JULIA_DEPOT_PATH="/home/app/.julia"
ENV APP_HOST="0.0.0.0"
ENV APP_PORT=8000
EXPOSE ${APP_PORT}

# instantiate Julia packages with only config files copied for better caching
USER app

# copy the full app and precompile
COPY --chown=app:app src /home/app/${APP}/
COPY --chown=app:app LarderORM /home/app/${APP}/
COPY --chown=app:app *.toml precompile.jl /home/app/${APP}/
COPY --chown=app:app dashboard/dist /home/app/${APP}/dashboard/
COPY --chown=app:app indices/dist /home/app/${APP}/indices/
RUN julia --project -e 'using Pkg; Pkg.precompile();'

RUN julia --project precompile.jl

# set an environment variable to indicate that we are running inside Docker
ENV DOCKER="true"

# run app
ENTRYPOINT ["julia", "--project", "--threads=auto", "-m", "Larder"]
