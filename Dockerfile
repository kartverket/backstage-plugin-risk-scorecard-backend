FROM eclipse-temurin:21.0.2_13-jre-alpine AS build
COPY . .
RUN ./gradlew build -x test
RUN apk update && apk upgrade

FROM eclipse-temurin:21

# Create application directory and subdirectories
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar

# Install socat only if running locally
ARG LOCAL
ENV LOCAL $LOCAL
RUN if [ $LOCAL ] ; then apt update && apt install -y socat ; fi

# Add non-root user og endre rettigheter
RUN useradd user && chown -R user:user /app /app/logs /app/tmp

# Bytt til non-root user
USER user

EXPOSE 8080 8081

COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

