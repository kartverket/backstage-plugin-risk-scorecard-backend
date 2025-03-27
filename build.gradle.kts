import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    kotlin("plugin.serialization") version "2.1.20"
}

// Oppgradeer til siste versjon av ktlint fordi org.jlleitschuh.gradle.ktlint version 12.1.2 bruker for gammel versjon
// Slett når ktlint-plugin'en oppdateres til nyere versjon
ktlint {
    version.set("1.5.0")
}

group = "no"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://dist.wso2.org/maven2/public")
    }
}

val kotlinVersion = "2.1.20"
val springBootVersion = "3.4.4"
val springSecurityVersion = "6.4.4"
val kotlinxSerializationVersion = "1.8.0"
val kotlinxCoroutinesVersion = "1.10.1"
val nettyVersion = "4.1.119.Final"
val micrometerVersion = "1.14.5"
val fasterXmlJacksonVersion = "2.18.3"
val kotlinJsonSchemaVersion = "0.56"
val apacheCommonsVersion = "3.17.0"
val googleGsonVersion = "2.12.1"
val googleAuthVersion = "1.33.1"
val googleGuavaVersion = "33.4.6-jre"
val nimbusdsVersion = "10.0.2"
val bouncyCastleVersion = "1.80"
val jsonWebTokenVersion = "0.12.6"
val ninjaSquadVersion = "4.0.2"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("io.netty:netty-all:$nettyVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$fasterXmlJacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$fasterXmlJacksonVersion")
    implementation("net.pwall.json:json-kotlin-schema:$kotlinJsonSchemaVersion")
    implementation("org.apache.commons:commons-lang3:$apacheCommonsVersion")

    implementation("com.google.code.gson:gson:$googleGsonVersion")
    implementation("com.google.auth:google-auth-library-credentials:$googleAuthVersion")
    implementation("com.google.guava:guava:$googleGuavaVersion")

    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")

    implementation("io.jsonwebtoken:jjwt-api:$jsonWebTokenVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jsonWebTokenVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jsonWebTokenVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("com.ninja-squad:springmockk:$ninjaSquadVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
