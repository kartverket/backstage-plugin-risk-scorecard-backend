ARG BUILD_IMAGE=eclipse-temurin:24.0.2_12-jdk-alpine-3.22
ARG IMAGE=eclipse-temurin:25.0.1_8-jre-alpine-3.23

# Make sure the logic is in sync with Dockerfile.M4
FROM ${BUILD_IMAGE} AS build
COPY . .

# Get security updates
RUN apk upgrade --no-cache

RUN ./gradlew build -x test

FROM ${IMAGE}

# Create application directory and subdirectories.
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar

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
