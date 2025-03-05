FROM eclipse-temurin:23.0.2_7-jdk-alpine-3.21 as build
COPY . .
RUN ./gradlew build -x test

FROM eclipse-temurin:23.0.2_7-jre-alpine-3.21

# Create application directory and subdirectories
RUN mkdir -p /app /app/logs /app/tmp

COPY --from=build /build/libs/*.jar /app/backend.jar

EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]