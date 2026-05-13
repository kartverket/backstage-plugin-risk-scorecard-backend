#!/bin/bash
set -a
source .env
set +a
./gradlew bootRun --args="--spring.profiles.active=local" 