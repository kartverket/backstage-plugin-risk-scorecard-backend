ARG BUILD_IMAGE=eclipse-temurin:25.0.3_9-jre-ubi10-minimal@sha256:fadae232b6a4ff83dec0a6a6474c27aaf2e4fea8efe9a4d7b59aae62edad8c0b
# We use the eclipse-temurin 'minimal' image for build stage.
ARG SOPS_BUILD_IMAGE=golang:1.26.2
ARG SOPS_VERSION_ARG=3.12.2
ARG SOCAT_VERSION_ARG=tag-1.8.1.1
# Fetch distroless images from Google's gcr.io.
ARG DISTROLESS_IMAGE=gcr.io/distroless/java25@sha256:c0d379ff54ea6d61f3f35736e8fdc66c91fb96f645a86a6ba2530a45d95b8841

# Build stage for Java app
FROM ${BUILD_IMAGE} AS build
COPY . .

RUN ./gradlew build -x test

# Build SOPS from source
FROM --platform=$BUILDPLATFORM ${SOPS_BUILD_IMAGE} AS sops_build
ARG TARGETOS
ARG TARGETARCH
ARG SOPS_VERSION_ARG
ARG SOPS_TAG=v${SOPS_VERSION_ARG}
WORKDIR /src
RUN git clone --depth 1 --branch "${SOPS_TAG}" https://github.com/getsops/sops.git
WORKDIR /src/sops/cmd/sops
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/sops .

# Build entrypoint binary
FROM --platform=$BUILDPLATFORM golang:1.26.2 AS entrypoint_build
WORKDIR /src
COPY docker-entrypoint.go .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/entrypoint docker-entrypoint.go

# Build socat from source
FROM --platform=$BUILDPLATFORM alpine:3.23.4 AS socat_build
ARG SOCAT_VERSION_ARG
RUN apk add --no-cache \
        build-base linux-headers git bash autoconf \
        openssl-dev openssl-libs-static \
        readline-dev readline-static ncurses-static && \
    git clone --depth 1 --branch ${SOCAT_VERSION_ARG} https://repo.or.cz/socat.git
WORKDIR /socat
RUN autoconf && \
    sh ./configure LDFLAGS="-static" && \
    make && \
    mkdir -p /out && cp socat /out/socat

# Final distroless image — production
FROM ${DISTROLESS_IMAGE} AS production

WORKDIR /app

# Copy Java app
COPY --from=build /build/libs/*.jar /app/backend.jar

# Copy binaries
COPY --from=sops_build /out/sops /usr/bin/sops
COPY --from=entrypoint_build /out/entrypoint /entrypoint

EXPOSE 8080 8081

USER nonroot
ENTRYPOINT ["/entrypoint"]

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Local dev image — extends production, adds socat for port forwarding
FROM production AS local
COPY --from=socat_build /out/socat /usr/bin/socat
