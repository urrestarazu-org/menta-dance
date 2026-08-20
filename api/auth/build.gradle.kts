import com.menta.buildlogic.registerLayeredCoverageVerification

plugins {
    id("java-library")
}

description = "Authentication and user management module"

dependencies {
    // Shared module
    implementation(project(":api:shared"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")

    // MapStruct for mapping between layers
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("com.h2database:h2")
}

tasks.jar {
    enabled = true
}

// PR3 coverage gates (ADR-0021 strict, openspec/config.yaml profile_100).
//
// The mechanism — BUNDLE aggregation, scoping via classDirectories, and the
// #96 finding behind it — lives in buildSrc's registerLayeredCoverageVerification.
// What stays here is the policy: which layers this module gates, at what
// minimum, and why.
//
//   - Domain + Application: 1.00 LINE (BUNDLE). Real: 99.7%, and the 0.3%
//     gap is the excluded class below.
//   - Infrastructure: 0.65 LINE (BUNDLE). Real: 66.0% — controllers,
//     mappers, JPA entities and JWT security wiring are exercised
//     end-to-end, so a per-class 1.00 would add ceremony, not safety. The
//     minimum sits just under the real number so the gate acts as a
//     ratchet: it cannot slide back, and it rises when the layer does.
val jacocoDomainApplicationCoverageVerification = registerLayeredCoverageVerification(
    "jacocoDomainApplicationCoverageVerification", "1.00",
    listOf("com/menta/auth/domain/**", "com/menta/auth/application/**"),
    // GuaranteedAlgorithm isolates the one catch(NoSuchAlgorithmException)
    // branch that's structurally unreachable: the JCA spec mandates every
    // conformant JDK support "SHA-256" (java.security.MessageDigest
    // javadoc), so triggering it means mutating java.security.Security's
    // provider list at runtime — exactly the fragile, provider-dependent
    // test the 100% gate exists to avoid writing. Excluded by name rather
    // than lowering the bundle minimum, and kept to that one tiny file
    // (not the whole Sha256Hex class) so growing Sha256Hex's real hashing
    // logic stays fully covered by this gate — see the class's own
    // Javadoc.
    excludePatterns = listOf("com/menta/auth/domain/crypto/GuaranteedAlgorithm.class")
)
val jacocoInfrastructureCoverageVerification = registerLayeredCoverageVerification(
    "jacocoInfrastructureCoverageVerification", "0.65",
    listOf("com/menta/auth/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
