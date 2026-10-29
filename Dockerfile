# syntax=docker/dockerfile:1.7

ARG SOPS_VERSION_ARG=3.13.3

# Build stage for Java app
FROM ghcr.io/kartverket/emudhra-images/jdk/dockerhub:jdk-25.0-alpine3.24@sha256:9040fd4372ac7c4581712c77ba9324a175ff9f9679ab3ff60ea5c12eb02fcdb9 AS build
WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew build -x test

### Build SOPS from source ###
FROM --platform=$BUILDPLATFORM golang:1.27.3 AS go_build
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

FROM ghcr.io/kartverket/emudhra-images/jre/dhi:jre-25.0-alpine3.24@sha256:0560e1680f602893991ce298a03975ebf3cc467fc0b01275ed1d83d03e013044 AS production

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/backend.jar
COPY --from=go_build --chown=root:root --chmod=0755 /out/sops /usr/bin/sops

EXPOSE 8080 8081

# Hardened DHI runtime images already run as a non-root user and ship no
# package manager or shell, so run the JVM directly as PID 1.
USER nonroot

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Local dev image — non-hardened base with a shell + apk to add socat, so it can relay traffic to host.docker.internal.
FROM ghcr.io/kartverket/emudhra-images/jdk/dockerhub:jdk-25.0-alpine3.24@sha256:9040fd4372ac7c4581712c77ba9324a175ff9f9679ab3ff60ea5c12eb02fcdb9 AS local

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