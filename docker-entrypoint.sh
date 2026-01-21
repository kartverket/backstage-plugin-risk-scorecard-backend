#!/bin/sh

# If ran locally ($LOCAL is set), then we need to redirect traffic from
# localhost:7007 to host.docker.internal:7007 for it to reach the
# backstage auth service, while the JWT issuer remains correct.
# Do the same for port 8084 to the crypto service, to be able to use
# the same .env file with and without docker.
if [ "$LOCAL" ] ; then
    echo "Starting socat relay service"
    exec socat tcp-l:7007,fork, tcp:host.docker.internal:7007 &
    exec socat tcp-l:8084,fork, tcp:host.docker.internal:8084 &
    exec socat tcp-l:8888,fork, tcp:host.docker.internal:8888
fi

# Then exec the container's main process (what's set as CMD in the Dockerfile).
exec "$@"
