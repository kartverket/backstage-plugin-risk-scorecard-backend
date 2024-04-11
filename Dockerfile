FROM eclipse-temurin:21 as build
COPY . .
RUN ./gradlew build -x test

FROM eclipse-temurin:21

ARG SOPS_AMD64="https://github.com/MagnusTonnessen/sops/releases/download/v1.0/sops-v1.0.linux.amd64"
ARG SOPS_ARM64="https://github.com/MagnusTonnessen/sops/releases/download/v1.0/sops-v1.0.linux.arm64"

ARG TARGETARCH
RUN if [ "$TARGETARCH" = "amd64" ]; then \
      curl -L $SOPS_AMD64 -o /usr/local/bin/sops; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
      curl -L $SOPS_ARM64 -o /usr/local/bin/sops; \
    else \
      echo "Unsupported architecture"; \
    fi \
    && chmod +x /usr/local/bin/sops

COPY --from=build /build/libs/*.jar backend.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "backend.jar"]
