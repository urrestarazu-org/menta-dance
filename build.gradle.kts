import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.jvm.tasks.Jar
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    jacoco
    checkstyle
}

group = "com.menta"
version = "0.1.0-SNAPSHOT"

val springBootVersion = libs.versions.spring.boot.get()
val checkstyleVersion = libs.versions.checkstyle.get()
val jacocoVersion = libs.versions.jacoco.get()

// Whole-module LINE coverage floors, for the modules that do NOT declare
// per-layer gates of their own. Keep each value just under the module's
// current real coverage — a floor set far below what a module already
// achieves protects nothing, it only lets the suite rot in silence.
// Raise these when the module genuinely improves; never lower them to make
// a red build green.
val moduleCoverageFloor = mapOf(
    ":api:shared" to "0.85", // real 97.1%
    ":api:app" to "0.90",    // real 97.4%
    ":bff" to "0.85"         // real 94.9%
)

// Checkstyle warning ratchet (#298, ADR-0043). Maximum warnings allowed per
// Checkstyle task, keyed by task path. Fix or lower, never raise: a cleanup PR
// re-measures and commits the exact count of every task it touches. A task
// with no entry (and whose module is not strict) fails the build.
// Measure: ./gradlew <checkstyle tasks> --rerun-tasks --no-build-cache --continue
val checkstyleWarningCeiling = mapOf(
    ":api:app:checkstyleMain" to 17,
    ":api:app:checkstyleTest" to 46,
    ":api:auth:checkstyleMain" to 127,
    ":api:auth:checkstyleTest" to 13,
    ":api:billing:checkstyleMain" to 183,
    ":api:billing:checkstyleTest" to 40,
    ":api:physical:checkstyleMain" to 137,
    ":api:physical:checkstyleTest" to 10,
    ":api:shared:checkstyleMain" to 5,
    ":api:shared:checkstyleTest" to 0,
    ":api:virtual:checkstyleMain" to 181,
    ":api:virtual:checkstyleTest" to 12,
    ":bff:checkstyleMain" to 9,
    ":bff:checkstyleTest" to 27,
)

// Modules that reached 0 warnings: both Checkstyle tasks run with severity
// `error` and need no ceiling entry. A module is added here in the same PR
// that deletes its two ceiling keys.
// `:api` is the aggregator project of the api modules: it has no sources (its
// Checkstyle tasks are NO-SOURCE) but `./gradlew build` realizes them, so it
// starts strict with zero findings.
val checkstyleStrictModules = setOf(":api")

// Modules whose coverage feeds the aggregated report below.
val jvmCoverageModules = listOf(
    ":api:shared", ":api:auth", ":api:billing", ":api:virtual", ":api:physical", ":api:app", ":bff"
)

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    google()
    mavenCentral()
}

tasks.register<Exec>("verifyLocalInfrastructureContract") {
    group = "verification"
    description = "Validates the local infrastructure and persistence scaffold contract."
    commandLine("bash", "${rootDir}/scripts/verify-local-infrastructure-contract.sh")
}

tasks.named("check") {
    dependsOn("verifyLocalInfrastructureContract")
}

// Monorepo-wide coverage report.
//
// Every per-module report only ever sees its own test task's execution data,
// so the integration suite in :api:app — which drives virtual, auth and
// billing end to end through the real filter chain and a Testcontainers
// MySQL — is invisible to every module gate it actually exercises. This task
// merges all execution data over all main classes, which is the only view
// that answers "how much of this system is actually covered".
//
// Reporting only: no thresholds hang off it. Gates stay per module, where a
// failure names the layer that regressed instead of a global number nobody
// owns.
val jacocoAggregatedReport = tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Merges JaCoCo execution data from every JVM module into a single report."
    reports {
        xml.required = true
        html.required = true
    }
}

gradle.projectsEvaluated {
    val reported = jvmCoverageModules.map { project(it) }
    jacocoAggregatedReport.configure {
        reported.forEach { dependsOn("${it.path}:test") }
        // filter { it.exists() } stays lazy: a module whose tests have not run
        // yet contributes nothing instead of failing the whole report.
        executionData.setFrom(
            files(reported.map { it.layout.buildDirectory.file("jacoco/test.exec") })
                .filter { it.exists() }
        )
        classDirectories.setFrom(
            files(reported.map { it.extensions.getByType<SourceSetContainer>()["main"].output.classesDirs })
        )
        sourceDirectories.setFrom(
            files(reported.map { it.extensions.getByType<SourceSetContainer>()["main"].allJava.srcDirs })
        )
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        google()
        mavenCentral()
    }

    if (project.path.startsWith(":api") || project.name == "bff") {
        apply(plugin = "java-library")
        apply(plugin = "org.springframework.boot")
        apply(plugin = "io.spring.dependency-management")
        apply(plugin = "jacoco")
        apply(plugin = "checkstyle")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        dependencies {
            add("implementation", platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
            add("testImplementation", "org.springframework.boot:spring-boot-starter-test")

            // Force JUnit Platform 1.12.2 to override Gradle's bundled 1.8.2
            add("testRuntimeOnly", "org.junit.platform:junit-platform-engine:1.12.2")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.12.2")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-commons:1.12.2")

            // Only add ArchUnit to modules that have architecture tests
            if (project.name != "shared" && project.parent?.name == "api") {
                add("testImplementation", "com.tngtech.archunit:archunit-junit5:1.3.0")
            }
        }

        // JUnit Platform version alignment handled by Spring Boot BOM

        configurations.configureEach {
            resolutionStrategy.activateDependencyLocking()
        }

        // Disable bootJar for library modules (only app and bff need executable JARs)
        if (project.name !in listOf("app", "bff")) {
            tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
                enabled = false
            }
            tasks.named<Jar>("jar") {
                enabled = true
            }
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }

        val strict = project.path in checkstyleStrictModules
        checkstyle {
            toolVersion = checkstyleVersion
            configFile = rootProject.file("config/checkstyle/google_checks.xml")
            // Severity is flipped through configProperties: the -D system
            // property is not seen by the Checkstyle worker.
            if (strict) configProperties = mapOf("org.checkstyle.google.severity" to "error")
        }

        tasks.withType<Checkstyle>().configureEach {
            val ceiling = checkstyleWarningCeiling[path]
            maxWarnings = when {
                strict -> 0.also {
                    if (ceiling != null) logger.warn("Stale Checkstyle ceiling for $path (strict module); remove it.")
                }
                ceiling != null -> ceiling
                else -> throw GradleException(
                    "No Checkstyle warning ceiling for $path: add it to checkstyleWarningCeiling " +
                        "in the root build.gradle.kts with its measured count (ADR-0043)."
                )
            }
        }

        jacoco {
            toolVersion = jacocoVersion
        }

        tasks.jacocoTestReport {
            dependsOn(tasks.test)
            reports {
                xml.required = true
                html.required = true
            }
        }

        tasks.jacocoTestCoverageVerification {
            dependsOn(tasks.jacocoTestReport)
            violationRules {
                // Whole-module LINE floor, applied to every JVM module.
                //
                // auth, billing, virtual and physical gate per Clean
                // Architecture layer instead, through their own
                // registerLayeredCoverageVerification tasks (buildSrc) — this
                // rule stays at 0.00 for them so the two mechanisms don't
                // fight, and their real gates hang off `check`.
                //
                // The remaining modules — shared, app, bff — had no gate at
                // all until now, which is how :api:shared drifted to 60% while
                // being the contract every other module depends on. They get a
                // ratchet just under their current real number: it cannot
                // slide back, and it is raised when the module improves.
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = moduleCoverageFloor
                            .getOrDefault(project.path, "0.00")
                            .toBigDecimal()
                    }
                }
            }
        }

        tasks.check {
            dependsOn(tasks.jacocoTestCoverageVerification)
        }
    }
}
