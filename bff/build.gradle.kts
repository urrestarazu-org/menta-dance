import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("org.springframework.boot")
    alias(libs.plugins.node.gradle)
}

description = "Backend for Frontend web application"

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Session custody
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")

    // WebClient for calling API
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Thymeleaf extras
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

// Tailwind CSS pipeline (ADR-0024, #536).
//
// Node is provisioned by Gradle (download = true), pinned to the ADR-0024
// baseline, so the build never depends on the developer's or the runner's Node.
// The runtime is cached under bff/.gradle/nodejs (ignored by Git).
node {
    download = true
    version = "20.11.1"
}

// Reproducible install: `npm ci` restores exactly what package-lock.json says
// and fails if package.json and the lockfile disagree.
tasks.npmInstall {
    npmCommand = listOf("ci")
}

val tailwindOutputRoot = layout.buildDirectory.dir("generated/tailwind")

val tailwindBuild = tasks.register<NpmTask>("tailwindBuild") {
    group = "build"
    description = "Compiles and minifies the Tailwind CSS served at /css/app.css."
    dependsOn(tasks.npmInstall)
    npmCommand = listOf("run")
    args = listOf("build")
    // Keep the build log free of the "caniuse-lite is outdated" notice: the
    // browser data is pinned by the lockfile on purpose.
    environment.put("BROWSERSLIST_IGNORE_OLD_DATA", "true")

    inputs.files("package.json", "package-lock.json", "tailwind.config.js", "postcss.config.js")
        .withPropertyName("toolingConfig")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/frontend")
        .withPropertyName("frontendSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // The content scan reads the Thymeleaf templates: a new class there must
    // re-run Tailwind.
    inputs.dir("src/main/resources/templates")
        .withPropertyName("templates")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(tailwindOutputRoot.map { it.dir("static/css") })
}

// The generated root holds static/css/app.css, so it lands on the classpath as
// static/css/app.css and Spring serves it at /css/app.css.
sourceSets.main {
    resources.srcDir(files(tailwindOutputRoot).builtBy(tailwindBuild))
}

tasks.bootJar {
    enabled = true
    archiveFileName = "menta-dance-bff.jar"
}

tasks.jar {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Load .env file and pass to Spring Boot
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .forEach { (key, value) ->
                environment(key.trim(), value.trim())
            }
    }
}
