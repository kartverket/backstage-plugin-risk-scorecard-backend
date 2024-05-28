FROM eclipse-temurin:21.0.2_13-jre-alpine@sha256:f153dfdd10e9846963676aa6ea8b8630f150a63c8e5fe127c93e98eb10b86766
RUN apk --no-cache add curl
EXPOSE 8080 8081
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar app/backend.jar

RUN curl -L "https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.linux.arm64" -o /usr/local/bin/sops && \
    chmod +x /usr/local/bin/sops && \
    /usr/local/bin/sops --version

RUN adduser -D user && chown -R user /app && chown -R user /usr/local/bin
USER user
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
