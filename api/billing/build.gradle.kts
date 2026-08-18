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

// US-BILLING-001 coverage gates (CLAUDE.md: 100% for billing.domain,
// billing.application). BUNDLE aggregation, not CLASS: a per-CLASS gate
// counts record/DTO boilerplate the same as behavior-bearing code, which
// punishes exactly the style Clean Architecture wants here (small,
// intention-revealing types). Mirrors :api:auth's identical rationale.
//   - Domain + Application: must reach 1.00 LINE together (BUNDLE).
//   - Infrastructure: best-effort 0.50 LINE (BUNDLE) — controllers, JPA
//     entities, mappers, config wiring are exercised end-to-end but a
//     per-class 1.00 gate does not add value there.
//
// #96: a JacocoViolationRule with element=BUNDLE never reports a violation
// once `includes`/`excludes` is set (confirmed empirically against this
// project's Gradle 9.7.0 / JaCoCo 0.8.12 — see docs/14-TEST-STRATEGY.md and
// api/auth/build.gradle.kts for the full writeup). Scoping therefore
// happens on the task's `classDirectories` INPUT (a FileTree filtered by
// package path), not on the rule itself — mirrors :api:auth exactly.
fun Project.registerLayeredCoverageVerification(
    taskName: String,
    minimumLineRatio: String,
    packagePatterns: List<String>,
    excludePatterns: List<String> = emptyList()
) = tasks.register<JacocoCoverageVerification>(taskName) {
    dependsOn(tasks.test)
    val mainSourceSet = sourceSets.getByName("main")
    val mainOutput = mainSourceSet.output
    executionData.setFrom(tasks.test.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! })
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    classDirectories.setFrom(
        mainOutput.classesDirs.asFileTree.matching {
            packagePatterns.forEach { include(it) }
            excludePatterns.forEach { exclude(it) }
        }
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = minimumLineRatio.toBigDecimal()
            }
        }
    }
}

val jacocoDomainApplicationCoverageVerification = registerLayeredCoverageVerification(
    "jacocoDomainApplicationCoverageVerification", "1.00",
    listOf("com/menta/billing/domain/**", "com/menta/billing/application/**")
)
val jacocoInfrastructureCoverageVerification = registerLayeredCoverageVerification(
    "jacocoInfrastructureCoverageVerification", "0.50",
    listOf("com/menta/billing/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
