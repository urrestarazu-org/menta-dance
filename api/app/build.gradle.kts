plugins {
    id("org.springframework.boot")
}

description = "Main Spring Boot application module"

tasks.withType<Test> {
    // Default Spring TestContext cache size is 32. This module has 40+ distinct
    // @SpringBootTest configurations (each unique profile + @MockBean combination is its own
    // cache key); once the LRU cache fills, evicting a "test"-profile context closes its
    // EntityManagerFactory, which issues DROP TABLE (ddl-auto: create-drop) against the shared,
    // persistent jdbc:h2:mem:testdb instance (DB_CLOSE_DELAY=-1 keeps it alive across contexts)
    // — wiping tables out from under any other still-active "test"-profile context sharing it.
    // Must be a JVM system property: the TestContext cache is sized before any individual
    // ApplicationContext (and therefore application-test.yml) is loaded.
    systemProperty("spring.test.context.cache.maxSize", "64")
}

dependencies {
    // All API modules
    implementation(project(":api:shared"))
    implementation(project(":api:auth"))
    implementation(project(":api:billing"))
    implementation(project(":api:virtual"))
    implementation(project(":api:physical"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")

    // Flyway for migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // #209 Phase D: JavaMailSender.send is overloaded on SimpleMailMessage AND
    // jakarta.mail.internet.MimeMessage — resolving that overload at compile
    // time (even when only the SimpleMailMessage variant is ever called) needs
    // jakarta.mail on this module's own test classpath, which "implementation"
    // deps on :api:auth/:api:billing do not expose transitively.
    testImplementation("org.springframework.boot:spring-boot-starter-mail")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("com.h2database:h2")
}

tasks.bootJar {
    enabled = true
    archiveFileName = "menta-dance-api.jar"
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

    // OpenTelemetry Java Agent for automatic instrumentation
    val agentFile = rootProject.file("observability/opentelemetry-javaagent.jar")
    if (agentFile.exists()) {
        jvmArgs(
            "-javaagent:${agentFile.absolutePath}",
            "-Dotel.service.name=menta-dance-api",
            "-Dotel.exporter.otlp.endpoint=http://localhost:4318",
            "-Dotel.exporter.otlp.protocol=http/protobuf",
            "-Dotel.logs.exporter=otlp",
            "-Dotel.metrics.exporter=none",
            "-Dotel.traces.exporter=none"
        )
    }
}
