import org.gradle.api.tasks.testing.Test
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar

plugins {
    java
    // Android plugins commented temporarily - require Google Maven repository in buildscript
    // alias(libs.plugins.android.application) apply false
    // alias(libs.plugins.android.library) apply false
    // alias(libs.plugins.kotlin.android) apply false
    // alias(libs.plugins.kotlin.kapt) apply false
    // alias(libs.plugins.kotlin.compose) apply false
    // alias(libs.plugins.hilt.android) apply false
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

        checkstyle {
            toolVersion = checkstyleVersion
            configFile = rootProject.file("config/checkstyle/google_checks.xml")
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
                rule {
                    limit {
                        // TODO: Increase to 0.50 when implementing tests in Phase 1
                        // Phase 0 scaffolding has no implementation yet
                        minimum = "0.00".toBigDecimal()
                    }
                }
            }
        }

        tasks.check {
            dependsOn(tasks.jacocoTestCoverageVerification)
        }
    }
}
