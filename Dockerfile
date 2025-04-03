# Make sure the logic is in sync with Dockerfile.M4
FROM eclipse-temurin:23-jre-alpine AS build
COPY . .

# Get security updates
RUN apk update && apk upgrade

RUN ./gradlew build -x test

FROM eclipse-temurin:23-alpine

# Create application directory and subdirectories.
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar

# Get security updates
RUN apk update && apk upgrade

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
