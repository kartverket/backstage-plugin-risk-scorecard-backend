FROM eclipse-temurin:21.0.2_13-jre-alpine as build
COPY . .
RUN ./gradlew build -x test
RUN apk update && apk upgrade

FROM eclipse-temurin:21

# Create application directory and subdirectories
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar
COPY --from=build /src/main/resources/schemas schemas

# Add non-root user og endre rettigheter
RUN useradd user && chown -R user:user /app /app/logs /app/tmp

# Bytt til non-root user
USER user

EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]

