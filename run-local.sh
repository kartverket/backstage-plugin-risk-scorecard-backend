#!/bin/bash
set -a
source .env.local
set +a
./gradlew bootRun --args="--spring.profiles.active=local" 