plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.allopen") version "2.3.0"
    id("io.quarkus") version "3.33.1"
}

group = "dev.quatrion"
// Domyslnie: najnowsze wydanie z Maven Central (alias "latest", patrz release.yml
// w quatrion-saas — publikuje rownolegle wersje CalVer i alias "latest").
// Wyjatek awaryjny (brak dostepu do Central): lokalna publikacja SNAPSHOT
// przez build-and-publish-local.bat — patrz README, sekcja "Tryb awaryjny".
val quatrionPortalVersion = "latest"

repositories {
    mavenLocal()
    mavenCentral()
}



dependencies {
    // Quatrion Portal framework (domyslnie: alias "latest" z Maven Central)
    implementation(enforcedPlatform("dev.quatrion:quatrion-portal-bom:${quatrionPortalVersion}"))
    implementation("dev.quatrion:quatrion-portal-annotations:${quatrionPortalVersion}")
    implementation("dev.quatrion:quatrion-portal-runtime:${quatrionPortalVersion}")

    // Quarkus BOM
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.1"))
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


    // Amazon S3 (wymagane przez S3FileStorageService z quatrion-portal-runtime)
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-amazon-services-bom:3.33.1"))
    implementation("io.quarkiverse.amazonservices:quarkus-amazon-s3")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Tests
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")


}

java {
    // Kanoniczny toolchain: Temurin 21 (Foojay z settings.gradle.kts dociaga
    // dystrybucje automatycznie — build nie zalezy od lokalnego JDK).
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
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

