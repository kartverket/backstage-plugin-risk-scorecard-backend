#!/bin/bash
set -a
source .env.local
set +a
export JAVA_TOOL_OPTIONS="--add-opens java.base/java.nio=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true"
./gradlew bootRun --args="--spring.profiles.active=local" 