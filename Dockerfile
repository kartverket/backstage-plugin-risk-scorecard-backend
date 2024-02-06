FROM eclipse-temurin:21 as build
COPY . .
RUN ./gradlew build -x test

FROM eclipse-temurin:21
COPY --from=build /build/libs/*.jar backend.jar
COPY .security .security
ADD https://github.com/getsops/sops/releases/download/v3.8.1/sops-v3.8.1.linux.amd64 /usr/local/bin/sops
RUN chmod +x /usr/local/bin/sops

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "backend.jar"]
