#!/bin/bash

if [[ $LARDER_ENV == "dev" ]]; then
    npm run --prefix dashboard build-dev
else
    npm run --prefix dashboard build
fi

