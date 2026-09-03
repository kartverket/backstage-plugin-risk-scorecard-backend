# syntax=docker/dockerfile:1.7

ARG SOPS_VERSION_ARG=3.13.3

# Build stage for Java app
FROM dhi.io/eclipse-temurin:25-jdk-alpine-dev@sha256:04099db397673721bbb4e1e860815ad147f9a5c0d6468bdc2119b581bd48dfac AS build
WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew build -x test

### Build SOPS from source ###
FROM --platform=$BUILDPLATFORM golang:1.27.1 AS go_build
ARG TARGETOS
ARG TARGETARCH
ARG SOPS_VERSION_ARG
WORKDIR /src
RUN git clone --depth 1 --branch "v${SOPS_VERSION_ARG}" https://github.com/getsops/sops.git
WORKDIR /src/sops/cmd/sops
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/sops .

FROM dhi.io/eclipse-temurin:25-alpine@sha256:d637909e179731a82d0764f4726755d5ccde5a100431bc01a75b7f795977ed8f AS production

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/backend.jar
COPY --from=go_build --chown=root:root --chmod=0755 /out/sops /usr/bin/sops

EXPOSE 8080 8081

# Hardened DHI runtime images already run as a non-root user and ship no
# package manager or shell, so run the JVM directly as PID 1.
USER nonroot

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Local dev image — non-hardened base with a shell + apk to add socat, so it can relay traffic to host.docker.internal.
FROM eclipse-temurin:25-jre-alpine AS local

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/backend.jar
COPY --from=go_build --chmod=0755 /out/sops /usr/bin/sops

RUN apk --no-cache add socat

ENV LOCAL=true

COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 8080 8081

ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Use the health endpoint of the application to provide information through docker about the health state of the application
HEALTHCHECK --start-period=30s --start-interval=10s --interval=5m \
    CMD wget -O - --quiet --tries=1 http://localhost:8081/actuator/health | grep UP || exit 1