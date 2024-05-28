FROM eclipse-temurin:21.0.2_13-jre-alpine@sha256:f153dfdd10e9846963676aa6ea8b8630f150a63c8e5fe127c93e98eb10b86766
RUN apk --no-cache add curl
EXPOSE 8080 8081
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar app/backend.jar

# Define the SOPS URLs
ARG SOPS_DARWIN_AMD64="https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.darwin.amd64"
ARG SOPS_DARWIN_ARM64="https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.darwin.arm64"
ARG SOPS_LINUX_AMD64="https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.linux.amd64"
ARG SOPS_LINUX_ARM64="https://github.com/bekk/sops/releases/download/v1.0/sops-v1.0.linux.arm64"

# Download and install SOPS based on architecture
RUN apk --no-cache add coreutils && \
    ARCH=$(uname -m) && OS=$(uname -s | tr '[:upper:]' '[:lower:]') && \
    if [ "$OS" = "linux" ] && [ "$ARCH" = "x86_64" ]; then \
        echo "Downloading SOPS for linux amd64"; \
        curl -L $SOPS_LINUX_AMD64 -o /usr/local/bin/sops; \
    elif [ "$OS" = "linux" ] && [ "$ARCH" = "aarch64" ]; then \
        echo "Downloading SOPS for linux arm64"; \
        curl -L $SOPS_LINUX_ARM64 -o /usr/local/bin/sops; \
    elif [ "$OS" = "darwin" ] && [ "$ARCH" = "x86_64" ]; then \
        echo "Downloading SOPS for darwin amd64"; \
        curl -L $SOPS_DARWIN_AMD64 -o /usr/local/bin/sops; \
    elif [ "$OS" = "darwin" ] && [ "$ARCH" = "aarch64" ]; then \
        echo "Downloading SOPS for darwin arm64"; \
        curl -L $SOPS_DARWIN_ARM64 -o /usr/local/bin/sops; \
    else \
        echo "Unsupported architecture: $ARCH or OS: $OS"; \
        exit 1; \
    fi && \
    chmod +x /usr/local/bin/sops

RUN echo "Hello"

RUN adduser -D user && chown -R user /app && chown -R user /usr/local/bin
USER user
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]


