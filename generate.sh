#!/bin/bash

npm run --prefix indices build

if [[ $LARDER_ENV == "dev" ]]; then
    mkdir -p build/devResources/
    cp indices/dist build/devResources/indices -r
    npm run --prefix dashboard build-dev
else
    npm run --prefix dashboard build
fi
