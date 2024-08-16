FROM eclipse-temurin:21-jdk as build

WORKDIR /app

COPY . .

RUN ./gradlew clean build -x test

FROM eclipse-temurin:21-jre

COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app/backend.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]