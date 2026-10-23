# syntax=docker/dockerfile:1.7

ARG SOPS_VERSION_ARG=3.13.3

# Build stage for Java app
FROM dhi.io/eclipse-temurin:25-jdk-alpine-dev@sha256:be33fd5578e683f0d5db4537bde79486e01082af437146811168a917a4674acd AS build
WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew build -x test

### Build SOPS from source ###
FROM --platform=$BUILDPLATFORM golang:1.27.2 AS go_build
ARG TARGETOS
ARG TARGETARCH
ARG SOPS_VERSION_ARG
WORKDIR /src
RUN git clone --depth 1 --branch "v${SOPS_VERSION_ARG}" https://github.com/getsops/sops.git
WORKDIR /src/sops/cmd/sops
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/sops .

FROM ghcr.io/kartverket/emudhra-images/jre/dhi:jre-25.0-alpine3.24@sha256:f0b12aab0f96059700032dada95e0bf85e8e0e7404d92246370a0a752ed21c4d AS java-runtime

FROM alpine:3.24 AS cert-builder

COPY --from=java-runtime /usr/lib/jvm/temurin-25/lib/security/blocked.certs /blocked.certs

RUN <<'EOF'
set -eu
blocked="/blocked.certs"
test -f "$blocked"
grep -qx 'Algorithm=SHA-256' "$blocked"
cat <<'CERTS' >> "$blocked"

# eMudhra compromised intermediate CAs (2026-10-20)
# EM DV TLS CA - G3B (issued by emSign Root TLS CA - G3)
42C4ACCD12C4F59597925E1A08A7A90102608543809308D50EC427E118BA1493
# EM DV TLS CA - G3B (issued by AAA Certificate Services)
45A883F349AEDFD5F3A649B5498FFB121712BA8B448F3F2A91DBC5A766FCDDBE
# EM OV TLS CA - G3B (issued by emSign Root TLS CA - G3)
98B896625C0C230ADEEC3EB2CF6AAD5001ECF9156A68644E82374CE5833BC25B
# EM OV TLS CA - G3B (issued by AAA Certificate Services)
5F1DED4DD590AF74631290CF073B122A2AAC6A1FA479778A0BD43579BD832699
# EM EV TLS CA - G3B (issued by emSign Root TLS CA - G3)
E7568FE6E12331F4FD7DE61DA4C7559D0F8EB192A218D4741A3BA75184333838
# EM EV TLS CA - G3B (issued by AAA Certificate Services)
579480AEA166290BD3C643589E795FC0D0E54E86505D180F1DFF99EFD3199512
# EM DV TLS CA - G3C (issued by emSign Root TLS CA - G3)
0164D7CEAE5BB909C272AF151FF44B1177AE9B47D9424B94F1D9558C30934D19
# EM DV TLS CA - G3C (issued by AAA Certificate Services)
62B7F9868263F72F6911FC78CEAF72C1979158B7368BF648683BB2AA39B02E72
# EM OV TLS CA - G3C (issued by emSign Root TLS CA - G3)
CB6E15165AC802E9B3F993F16C25D41F4E81C21EBDE71AE1B012AC00BF9C7C3F
# EM OV TLS CA - G3C (issued by AAA Certificate Services)
7485B51C0ED192878FA1D2F948FB529AE1842CC5086C4B67F7BE5B2039DE9489
# EM EV TLS CA - G3C (issued by emSign Root TLS CA - G3)
432EC25F0D28DE9006D97BF752F17BBB2CD41F669933FCB072D9A1148D0261F3
# EM EV TLS CA - G3C (issued by AAA Certificate Services)
FD9D5C2DBBD4995AB69B8D50C439D9527B00529199C2FD01B2174EB58F07021F
# EM OV CS CA - G3B (issued by emSign Root CS CA - G3)
9E39DE2881273ACC225A149C72BA3E0F51424963669AB633EE1DC7DF5DB067B4
# EM OV CS CA - G3B (issued by AAA Certificate Services)
A496E23AC0CAB8155FC9B117B97EAE368EFCBB9041AEC8804D5A308D2D01C2D8
# EM OV CS CA - G3C (issued by emSign Root CS CA - G3)
4B2460A466F95FD40113BCDCCA2CEB29B3B093921DE45474DA7DF48874C630B1
# EM OV CS CA - G3C (issued by AAA Certificate Services)
84112972B8E59D14560AE7E7119654474AAD119990CF3722BFC1E3B91615E1FD
CERTS
EOF

FROM java-runtime AS production

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/backend.jar
COPY --from=go_build --chown=root:root --chmod=0755 /out/sops /usr/bin/sops
COPY --from=cert-builder /blocked.certs /usr/lib/jvm/temurin-25/lib/security/blocked.certs

EXPOSE 8080 8081

# Hardened DHI runtime images already run as a non-root user and ship no
# package manager or shell, so run the JVM directly as PID 1.
USER nonroot

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Local dev image — non-hardened base with a shell + apk to add socat, so it can relay traffic to host.docker.internal.
FROM eclipse-temurin:25-jre-alpine AS local

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/backend.jar
COPY --from=go_build --chmod=0755 /out/sops /usr/bin/sops

RUN apk --no-cache add socat

ENV LOCAL=true

COPY --chmod=0755 docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 8080 8081

ENTRYPOINT ["/docker-entrypoint.sh"]

CMD ["java", "--add-opens", "java.base/java.nio=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "/app/backend.jar"]

# Use the health endpoint of the application to provide information through docker about the health state of the application
HEALTHCHECK --start-period=30s --start-interval=10s --interval=5m \
    CMD wget -O - --quiet --tries=1 http://localhost:8081/actuator/health | grep UP || exit 1