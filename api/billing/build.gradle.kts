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
    // Authentication/GrantedAuthority for the physical pricing management endpoint
    // (US-BILLING-009, #37) -- same dependency virtual/physical already declare
    // for their own admin controllers.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Redis (plans rate limiter)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Mail (purchase-exception notification, #209 Phase C) -- same starter
    // api:auth already declares for account activation.
    implementation("org.springframework.boot:spring-boot-starter-mail")

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
    // Embedded DB for @DataJpaTest repository tests against a real derived query
    // (same convention as api/auth's ActivationTokenJpaRepositoryTest).
    testRuntimeOnly("com.h2database:h2")
    // Real MySQL 8 for PaymentProofRepositoryAdapterTest (#31, US-BILLING-003, design C5): the
    // "second save for the same paymentId overwrites the row" replacement semantics rest on a real
    // UNIQUE KEY constraint, which H2's create-drop schema does not reliably reproduce — same
    // rationale as api/virtual's and api/auth's module-level Testcontainers dependency.
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
}

tasks.jar {
    enabled = true
}

// US-BILLING-001 coverage gates. The mechanism lives in buildSrc's
// registerLayeredCoverageVerification; what stays here is the policy.
//
//   - Domain + Application: 0.85 LINE (BUNDLE). Real: ~98%. Settled at #138:
//     the project-wide floor (CLAUDE.md — "ningún umbral por debajo de
//     85%") is the accepted target here; closing the remaining real gaps
//     (MarkPurchaseAssignedUseCase, PhysicalPurchaseCheckoutResult) is
//     deliberately not required for this gate to reflect final policy.
//   - Infrastructure: 0.85 LINE (BUNDLE). Real: 92.9%.
val jacocoDomainApplicationCoverageVerification = registerLayeredCoverageVerification(
    "jacocoDomainApplicationCoverageVerification", "0.85",
    listOf("com/menta/billing/domain/**", "com/menta/billing/application/**")
)
val jacocoInfrastructureCoverageVerification = registerLayeredCoverageVerification(
    "jacocoInfrastructureCoverageVerification", "0.85",
    listOf("com/menta/billing/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
