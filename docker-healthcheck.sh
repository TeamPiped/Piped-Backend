#!/usr/bin/env sh

# If PORT env var is set, use it, otherwise default to 8080
PORT=${PORT:-8080}

curl -f http://localhost:$PORT/healthcheck || exit 1
