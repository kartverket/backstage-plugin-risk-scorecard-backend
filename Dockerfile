ARG BUILD_IMAGE=eclipse-temurin:25-jre-alpine
ARG SOPS_BUILD_IMAGE=golang:1.25.7
ARG SOPS_VERSION_ARG=3.11.0
ARG SOCAT_VERSION_ARG=1.8.1.1
ARG DISTROLESS_IMAGE=gcr.io/distroless/java25-debian13

# Build stage for Java app
FROM ${BUILD_IMAGE} AS build
COPY . .

# Get security updates
RUN apk upgrade --no-cache

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
FROM --platform=$BUILDPLATFORM golang:1.25.7 AS entrypoint_build
WORKDIR /src
COPY docker-entrypoint.go .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/entrypoint docker-entrypoint.go

# Build socat statically
FROM --platform=$BUILDPLATFORM alpine:latest AS socat_build
ARG SOCAT_VERSION_ARG
RUN apk add --no-cache gcc musl-dev make && \
    wget -q http://www.dest-unreach.org/socat/download/socat-${SOCAT_VERSION_ARG}.tar.gz -O - | tar xz && \
    cd socat-${SOCAT_VERSION_ARG} && \
    ./configure LDFLAGS="-static" && \
    make && \
    mkdir -p /out && \
    cp socat /out/socat

# Final distroless image
FROM ${DISTROLESS_IMAGE}

WORKDIR /app

# Copy Java app
COPY --from=build /build/libs/*.jar /app/backend.jar

# Copy binaries
COPY --from=sops_build /out/sops /usr/bin/sops
COPY --from=entrypoint_build /out/entrypoint /entrypoint
COPY --from=socat_build /out/socat /usr/bin/socat

EXPOSE 8080 8081

ENTRYPOINT ["/entrypoint"]

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]