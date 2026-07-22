# To update: docker buildx imagetools inspect eclipse-temurin:25.0.3_9-jre-ubi10-minimal
# Use the top-level "Digest:" value (Index Digest, safe for all platforms)
ARG BUILD_IMAGE=eclipse-temurin:25.0.3_9-jre-ubi10-minimal@sha256:f89f7e3544edf5424da4256a1cc54b2637ef85fb877ff5949c783ff7ec1a0a7c
# To update: docker buildx imagetools inspect gcr.io/distroless/java25:latest
# Use the top-level "Digest:" value (Index Digest, safe for all platforms)
ARG DISTROLESS_IMAGE=gcr.io/distroless/java25@sha256:73f2263db8defa233004a7c700fd81e25c8747a530c413bddf74367b68663468

ARG GO_BUILD_IMAGE=golang:1.26.5
ARG SOCAT_BUILD_IMAGE=alpine:3.24.1
ARG SOPS_VERSION_ARG=3.13.2
ARG SOCAT_VERSION_ARG=tag-1.8.1.3

# Build stage for Java app
FROM ${BUILD_IMAGE} AS build

COPY . .

RUN ./gradlew build -x test

# Build SOPS and entrypoint binaries
FROM --platform=$BUILDPLATFORM ${GO_BUILD_IMAGE} AS go_build
ARG TARGETOS
ARG TARGETARCH
ARG SOPS_VERSION_ARG
ARG SOPS_TAG=v${SOPS_VERSION_ARG}
WORKDIR /src/sops
RUN git clone --depth 1 --branch "${SOPS_TAG}" https://github.com/getsops/sops.git
WORKDIR /src/sops/sops/cmd/sops
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/sops .
WORKDIR /src/entrypoint
COPY docker-entrypoint.go .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/entrypoint docker-entrypoint.go

# Build socat from source
FROM --platform=$BUILDPLATFORM ${SOCAT_BUILD_IMAGE} AS socat_build
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
COPY --from=go_build /out/sops /usr/bin/sops
COPY --from=go_build /out/entrypoint /entrypoint

EXPOSE 8080 8081

USER nonroot
ENTRYPOINT ["/entrypoint"]

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Local dev image — extends production, adds socat for port forwarding
FROM production AS local
COPY --from=socat_build /out/socat /usr/bin/socat
