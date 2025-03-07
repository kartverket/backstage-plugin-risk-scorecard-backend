#!/bin/bash

# If ran locally ($LOCAL is set), then we need to redirect traffic from
# localhost:7007 to host.docker.internal:7007 for it to reach the
# backstage auth service, while the JWT issuer remains correct.
if [ $LOCAL ] ; then
    echo "Starting socat relay service"
    exec socat tcp-l:7007,fork, tcp:host.docker.internal:7007 &
fi

exec java -jar /app/backend.jar
