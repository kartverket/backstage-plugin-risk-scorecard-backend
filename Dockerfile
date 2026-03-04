ARG BUILD_IMAGE=eclipse-temurin:25-jre-alpine
ARG IMAGE=eclipse-temurin:25-alpine
ARG SOPS_VERSION_ARG=3.11.0
ARG SOPS_TAG=v${SOPS_VERSION_ARG}

# Make sure the logic is in sync with Dockerfile.M4
FROM ${BUILD_IMAGE} AS build
COPY . .

# Get security updates
RUN apk upgrade --no-cache

RUN ./gradlew :app:build -x test

FROM golang:1.25.7 AS sops_build
ARG TARGETOS
ARG TARGETARCH
ARG SOPS_TAG

RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /src
RUN git config --global advice.detachedHead false && \
    git clone --depth 1 --branch "${SOPS_TAG}" https://github.com/getsops/sops.git

WORKDIR /src/sops/cmd/sops
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/sops .

FROM ${IMAGE}

# Create application directory and subdirectories.
RUN mkdir -p /app /app/logs /app/tmp

ARG SOPS_VERSION_ARG
ENV SOPS_VERSION=${SOPS_VERSION_ARG}
COPY --from=sops_build --chmod=0755 /out/sops /usr/bin/sops
COPY --from=build /app/build/libs/*.jar /app/backend.jar

# Get security updates
RUN apk upgrade --no-cache

# Install socat only if running locally.
ARG LOCAL
ENV LOCAL $LOCAL
RUN if [ "$LOCAL" ] ; then \
        apk --no-cache add socat ; \
    fi

# Add non-root user and change permissions.
RUN adduser -D user && chown -R user:user /app /app/logs /app/tmp

# Switch to non-root user.
USER user

EXPOSE 8080 8081

COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["java", "-jar", "/app/backend.jar"]

# Use the health endpoint of the application to provide information through docker about the health state of the application
HEALTHCHECK --start-period=30s --start-interval=10s --interval=5m \
    CMD wget -O - --quiet --tries=1 http://localhost:8081/actuator/health | grep UP || exit 1
