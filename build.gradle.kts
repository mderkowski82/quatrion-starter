plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.allopen") version "2.2.0"
    id("io.quarkus") version "3.23.0"
}

group = "com.example"
version = "0.0.1-RC4"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Quatrion Portal framework (local build)
    implementation(enforcedPlatform("dev.quatrion:quatrion-portal-bom:0.0.1-RC4"))
    implementation("dev.quatrion:quatrion-portal-annotations:0.0.1-RC4")
    implementation("dev.quatrion:quatrion-portal-runtime:0.0.1-RC4")

    // Quarkus BOM
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.23.0"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-hibernate-reactive-panache-kotlin")
    implementation("io.quarkus:quarkus-reactive-pg-client")
    implementation("io.quarkus:quarkus-arc")

    // REST + Jackson ObjectMapper
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")

    // Security / OIDC (SecurityIdentity)
    implementation("io.quarkus:quarkus-oidc")

    // Metrics (MeterRegistry)
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")

    // Health checks
    implementation("io.quarkus:quarkus-smallrye-health")

    // Tests
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-test-security")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Required for @PortalField on data-class constructor parameters (Java reflection)
        freeCompilerArgs.add("-java-parameters")
    }
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
    annotation("jakarta.persistence.Entity")
}

