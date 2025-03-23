# Make sure the logic is in sync with Dockerfile.M4
ARG BUILD_IMAGE=eclipse-temurin:23.0.2_7-jdk-alpine-3.21
ARG IMAGE=eclipse-temurin:23.0.2_7-jre-alpine-3.21

FROM ${BUILD_IMAGE} AS build
COPY . .
# Get security updates
RUN apk update && apk upgrade
RUN ./gradlew build -x test

FROM ${IMAGE}

# Create application directory and subdirectories.
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar

# Get security updates
RUN apt -y update && apt -y upgrade

# Install socat only if running locally.
ARG LOCAL
ENV LOCAL $LOCAL
RUN if [ "$LOCAL" ] ; then \
        apt install -y socat ; \
    fi

# Add non-root user and change permissions.
RUN useradd user && chown -R user:user /app /app/logs /app/tmp

# Switch to non-root user.
USER user

EXPOSE 8080 8081

COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["java", "-jar", "/app/backend.jar"]
