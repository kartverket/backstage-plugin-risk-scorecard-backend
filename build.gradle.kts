plugins {
    val kotlinPluginsVersion = "2.3.0"
    kotlin("jvm") version kotlinPluginsVersion
    kotlin("plugin.spring") version kotlinPluginsVersion
    kotlin("plugin.serialization") version kotlinPluginsVersion
    id("org.springframework.boot") version "3.5.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
}

ktlint {
    version.set("1.6.0")
}

group = "no"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

val kotlinVersion = "2.3.10"
val springBootVersion = "3.5.6"
val springSecurityVersion = "6.5.6"
val kotlinxSerializationVersion = "1.10.0"
val kotlinxCoroutinesVersion = "1.10.2"
val micrometerVersion = "1.16.2"
val jsonSchemaValidatorVersion = "3.0.0"
val nimbusdsVersion = "10.7"
val bouncyCastleVersion = "1.83"
val mockkVersion = "1.14.9"
val junitVersion = "6.0.2"
val springdocVersion = "2.8.4"

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

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion") {
        because("Auto-generates OpenAPI 3.0 specification and provides Swagger UI for API documentation.")
    }

    implementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion")) {
        because("The BOM (bill of materials) provides correct versions for all JUnit libraries used.")
    }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.bytebuddy:byte-buddy:1.18.5")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.18.5")

    testImplementation("io.mockk:mockk:$mockkVersion")

    constraints {
        implementation("org.apache.commons:commons-lang3:3.20.0") {
            because("Force secure version to fix CVE in transitive dependency from spring-boot-gradle-plugin")
        }
        implementation("io.netty:netty-codec-http2:4.2.7.Final") {
            because("Force specific version for transitive dependency")
        }
        implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.18") {
            because("Force secure version to fix vulnerability in version 10.1.43")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
