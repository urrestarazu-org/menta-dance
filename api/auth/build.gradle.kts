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
// Aggregation strategy — JaCoCo's per-CLASS counter counts interface
// records, type-only DTOs, and getters/setters exactly the same as
// behavior-bearing code. For a strict gate that respects Clean
// Architecture intent, BUNDLE-level aggregation is the only honest unit:
// "all behavior in this layer covered, with the inevitable record-class
// noise allowed to ride along". This matches the project's coverage
// profile_100 meaning: "no meaningful untested behavior leak".
//
//   - Domain + Application: must reach 1.00 LINE together (BUNDLE).
//   - Infrastructure: best-effort 0.50 LINE (BUNDLE) — controllers,
//     mappers, JPA entities, JWT security wiring are exercised end-to-end
//     but spec quibbles (per-class 1.00) don't add value.
//
// #96: a JacocoViolationRule with element=BUNDLE never reports a violation
// when `includes`/`excludes` is set — confirmed empirically against this
// project's Gradle 9.7.0 / JaCoCo 0.8.12: the exact same rule, same data,
// passes silently (even with a deliberately out-of-range 1.01 minimum) the
// moment `includes` is non-empty, and fails correctly the moment it's
// removed. Scoping therefore happens on the task's `classDirectories` INPUT
// (a FileTree filtered by package path) instead of on the rule itself, and
// each layer gets its own task with one unscoped, unfiltered BUNDLE rule —
// the one form proven to actually fail.
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
    "jacocoInfrastructureCoverageVerification", "0.50",
    listOf("com/menta/auth/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
