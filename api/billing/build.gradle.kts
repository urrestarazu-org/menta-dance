import com.menta.buildlogic.registerLayeredCoverageVerification

plugins {
    id("java-library")
}

description = "Billing and payments module"

dependencies {
    // Shared module
    implementation(project(":api:shared"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Redis (plans rate limiter)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Resilience4j (ADR-0023): timeout + circuit breaker for the Mercado Pago
    // payment-provider read. No retry module — the inbox's own backoff cycle
    // is the retry mechanism (ADR-0038), never stacked with Resilience4j's.
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")

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
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.jar {
    enabled = true
}

// US-BILLING-001 coverage gates. The mechanism lives in buildSrc's
// registerLayeredCoverageVerification; what stays here is the policy.
//
//   - Domain + Application: 1.00 LINE (BUNDLE). Real: 100%.
//   - Infrastructure: 0.70 LINE (BUNDLE). Real: 72.5% — controllers, JPA
//     entities, mappers and config wiring are exercised end-to-end, so a
//     per-class 1.00 adds no safety. The minimum sits just under the real
//     number so the gate ratchets instead of decorating.
val jacocoDomainApplicationCoverageVerification = registerLayeredCoverageVerification(
    "jacocoDomainApplicationCoverageVerification", "1.00",
    listOf("com/menta/billing/domain/**", "com/menta/billing/application/**")
)
val jacocoInfrastructureCoverageVerification = registerLayeredCoverageVerification(
    "jacocoInfrastructureCoverageVerification", "0.70",
    listOf("com/menta/billing/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
