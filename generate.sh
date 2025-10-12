#!/bin/bash

if [[ $APP_ENV == "dev" ]]; then
    npm run --prefix dashboard build-dev
else
    npm run --prefix dashboard build
fi

