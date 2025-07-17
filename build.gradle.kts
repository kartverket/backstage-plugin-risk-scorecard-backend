plugins {
    val kotlinPluginsVersion = "2.2.0"
    kotlin("jvm") version kotlinPluginsVersion
    kotlin("plugin.spring") version kotlinPluginsVersion
    kotlin("plugin.serialization") version kotlinPluginsVersion
    id("org.springframework.boot") version "3.5.3"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

ktlint {
    version.set("1.6.0")
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

val kotlinVersion = "2.2.0"
val springBootVersion = "3.5.3"
val springSecurityVersion = "6.5.1"
val kotlinxSerializationVersion = "1.9.0"
val kotlinxCoroutinesVersion = "1.10.2"
val micrometerVersion = "1.15.1"
val jsonSchemaValidatorVersion = "1.5.8"
val nimbusdsVersion = "10.3.1"
val bouncyCastleVersion = "1.81"
val mockkVersion = "1.14.5"
val junitVersion = "5.13.3"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
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
        because("Provides endpoints for health and event monitoring that are used in SKIP and Docker.")
    }

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
