ARG BUILD_IMAGE=eclipse-temurin:25.0.3_9-jre-ubi10-minimal@sha256:aa381f8933bc763a4a151325c0b8e41c37f08927208613038aeaa441ff48c448
# We use the eclipse-temurin 'minimal' image for build stage.
ARG SOPS_BUILD_IMAGE=golang:1.26.5
ARG SOPS_VERSION_ARG=3.13.2
ARG SOCAT_VERSION_ARG=tag-1.8.1.3
# Fetch distroless images from Google's gcr.io.
ARG DISTROLESS_IMAGE=gcr.io/distroless/java25@sha256:0aa10bfac55df3fed8ce238f4d35c5f14e9b705be763943e80b92d815e703201

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
