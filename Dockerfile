ARG BUILD_IMAGE=eclipse-temurin:25-jre-alpine
ARG IMAGE=eclipse-temurin:25-alpine
ARG SOPS_BUILD_IMAGE=golang:1.25.7
ARG SOPS_VERSION_ARG=3.11.0

# Make sure the logic is in sync with Dockerfile.M4
FROM ${BUILD_IMAGE} AS build
COPY . .

# Get security updates
RUN apk upgrade --no-cache

RUN ./gradlew build -x test

### Build SOPS from source ###
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

FROM ${IMAGE}

# Create application directory and subdirectories.
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar
COPY --from=sops_build --chown=root:root --chmod=0755 /out/sops /usr/bin/sops

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
