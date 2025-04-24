plugins {
    id("org.springframework.boot") version "3.4.4"
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    kotlin("plugin.serialization") version "2.1.20"
}

ktlint {
    version.set("1.5.0")
}

group = "no"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(23)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_23
}

repositories {
    mavenCentral()
}

val kotlinVersion = "2.1.20"
val springBootVersion = "3.4.4"
val springSecurityVersion = "6.4.5"
val kotlinxSerializationVersion = "1.8.1"
val kotlinxCoroutinesVersion = "1.10.2"
val nettyVersion = "4.2.0.Final"
val micrometerVersion = "1.14.6"
val fasterXmlJacksonVersion = "2.18.3"
val jsonSchemaValidatorVersion = "1.5.6"
val nimbusdsVersion = "10.2"
val bouncyCastleVersion = "1.80"
val mockkVersion = "1.14.0"
val junitVersion = "5.12.2"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.apache.tomcat.embed:tomcat-embed-core") {
        version {
            strictly("11.0.5")
        }
    }
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")

    implementation("org.springframework.security:spring-security-oauth2-jose:$springSecurityVersion")
    implementation("org.springframework.security:spring-security-oauth2-resource-server:$springSecurityVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion") {
        because("spring-security-oauth2-jose requires an external library for JWT encoding, like Nimbus-JOSE-JWT.")
    }
    runtimeOnly("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion") {
        because("Used by nimbus-jose-jwt for parsing of PEM certificates in dev/prod environments.")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion") {
        because("Provides endpoints for health and event monitoring that are used in SKIP.")
    }

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$fasterXmlJacksonVersion")
    implementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion")) {
        because("The BOM (bill of materials) provides correct versions for all JUnit libraries used.")
    }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
