plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group   = "io.infraforge"
version = "0.1.0-SNAPSHOT"

// ── Java 25 toolchain ─────────────────────────────────────────────────────────
// Gradle will automatically download JDK 25 via the toolchain resolver if it is
// not already installed locally. Run `./gradlew -version` to confirm.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ── Dependency BOM ────────────────────────────────────────────────────────────
dependencyManagement {
    imports {
        mavenBom(libs.aws.sdk.bom.get().toString())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ── Spring Boot ───────────────────────────────────────────────────────────
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2.client)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.actuator)

    // ── JWT ───────────────────────────────────────────────────────────────────
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // ── AWS SDK v2 ────────────────────────────────────────────────────────────
    implementation(libs.aws.dynamodb.enhanced)
    implementation(libs.aws.sqs)
    implementation(libs.aws.sesv2)
    implementation(libs.aws.s3)
    implementation(libs.aws.eventbridge)
    implementation(libs.aws.secretsmanager)
    implementation(libs.aws.sts)

    // ── Spring Statemachine (workflow engine — Phase 2) ───────────────────────
    implementation(libs.spring.statemachine.core)

    // ── Test ──────────────────────────────────────────────────────────────────
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.security.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading") // suppress JDK 21+ warnings in tests
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters") // needed for Spring MVC method parameter names
}
