import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
}

group = "no"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")

    implementation("io.netty:netty-all:4.1.100.Final")

    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("net.pwall.json:json-kotlin-schema:0.44")
    implementation("org.apache.commons:commons-lang3:3.0")

    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.google.auth:google-auth-library-credentials:1.23.0")
    implementation("com.google.cloud:google-cloud-secretmanager:2.35.0") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    implementation("com.nimbusds:nimbus-jose-jwt:9.39.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    environment["SOPS_AGE_KEY"] = "AGE-SECRET-KEY-12ZJZ9F3SSUGHTZMPMRX32R7MUT0C5JHWVND65643K8HDTMXQ0HZS4AHC08"
    environment["GCP_KMS_RESOURCE_PATH"] = "projects/spire-ros-5lmr/locations/eur4/keyRings/ROS/cryptoKeys/ros-as-code"
    environment["GITHUB_APP_ID"] = "828331"
    environment["GITHUB_APP_INSTALLATION_ID"] = "47304902"
    environment["PRIVATE_KEY_SECRET_NAME"] = "projects/spire-ros-5lmr/secrets/GITHUB_APP_PRIVATE_KEY/versions/1"
    environment["RISC_PATH"] = ".sikkerhet/risc"
    useJUnitPlatform()
}
