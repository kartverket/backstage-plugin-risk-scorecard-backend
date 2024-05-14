FROM eclipse-temurin:21.0.2_13-jre-alpine@sha256:f153dfdd10e9846963676aa6ea8b8630f150a63c8e5fe127c93e98eb10b86766
RUN apk --no-cache add curl
EXPOSE 8080
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar app/backend.jar

ARG SOPS_AMD64="https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.linux.amd64"
ARG SOPS_ARM64="https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.linux.arm64"
ARG TARGETARCH

RUN if [ "$TARGETARCH" = "amd64" ]; then \
      curl -L $SOPS_AMD64 -o /usr/local/bin/sops; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
      curl -L $SOPS_ARM64 -o /usr/local/bin/sops; \
    else \
      echo "Unsupported architecture"; \
    fi \
    && chmod +x -R /usr/local/bin/sops

RUN adduser -D user && chown -R user /app && chown -R user /usr/local/bin/sops
USER user
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
