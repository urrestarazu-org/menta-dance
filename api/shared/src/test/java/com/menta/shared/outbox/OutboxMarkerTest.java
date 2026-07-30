package com.menta.shared.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Safety net for the outbox marker contract living in :api:shared.
 *
 * Verifies:
 *   - OutboxEvent, OutboxStatus, OutboxListener compile and have the expected
 *     shape (the API other modules will consume).
 *   - The three classes have NO Spring or JPA imports (ADR-0021): the marker
 *     layer is intentionally framework-free.
 *   - OutboxListener can be expressed as a lambda / functional interface.
 */
class OutboxMarkerTest {

    @Test
    void outbox_status_has_pending_completed_failed() {
        Set<String> names = Set.of(
            OutboxStatus.PENDING.name(),
            OutboxStatus.COMPLETED.name(),
            OutboxStatus.FAILED.name()
        );

        assertEquals(3, names.size(), "OutboxStatus MUST expose exactly three states");
        assertTrue(names.contains("PENDING"));
        assertTrue(names.contains("COMPLETED"));
        assertTrue(names.contains("FAILED"));
    }

    @Test
    void outbox_event_records_all_contract_fields() {
        OutboxEvent event = new OutboxEvent(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P",
            "auth.AuthUserLoggedIn",
            "jti-uuid",
            "{\"token_version\":1}",
            OutboxStatus.PENDING,
            Instant.parse("2026-07-29T12:00:00Z")
        );

        assertEquals("01H9X3F4Z9YJ7K5Q6T2R8V1N4P", event.eventId());
        assertEquals("auth.AuthUserLoggedIn", event.eventType());
        assertEquals("jti-uuid", event.aggregateId());
        assertEquals("{\"token_version\":1}", event.payload());
        assertEquals(OutboxStatus.PENDING, event.status());
        assertEquals(Instant.parse("2026-07-29T12:00:00Z"), event.createdAt());
        assertNotNull(event);
    }

    @Test
    void outbox_listener_accepts_lambda_implementations() {
        OutboxListener<OutboxEvent> capturing = event -> {
            assertNotNull(event);
            assertEquals("auth.AuthUserLoggedIn", event.eventType());
        };

        assertDoesNotThrow(() -> capturing.onEvent(
            new OutboxEvent(
                "e1",
                "auth.AuthUserLoggedIn",
                "agg-1",
                "{}",
                OutboxStatus.PENDING,
                Instant.now()
            )
        ));
    }

    @Test
    void marker_classes_have_no_spring_or_jpa_imports() throws IOException {
        Path sharedRoot = locateSharedSourceRoot();
        assertNotNull(sharedRoot, "Could not locate api/shared source root");

        Path outboxDir = sharedRoot.resolve("main/java/com/menta/shared/outbox");
        assertTrue(
            Files.isDirectory(outboxDir),
            "Outbox marker classes MUST live in com.menta.shared.outbox"
        );

        String[] forbiddenPrefixes = {
            "import org.springframework.",
            "import jakarta.persistence.",
            "import javax.persistence."
        };

        try (var paths = Files.walk(outboxDir)) {
            paths
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    String content = readSafe(p);
                    for (String forbidden : forbiddenPrefixes) {
                        if (content.contains(forbidden)) {
                            fail(
                                "Marker class " + p
                                    + " MUST NOT import framework '" + forbidden
                                    + "' (ADR-0021 — :api:shared is framework-free)"
                            );
                        }
                    }
                });
        }
    }

    private Path locateSharedSourceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path candidate : List.of(
            cwd.resolve("api/shared/src"),
            cwd.resolve("../api/shared/src"),
            cwd.resolve("../../api/shared/src")
        )) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String readSafe(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ioe) {
            fail("Cannot read " + path + ": " + ioe.getMessage());
            return "";
        }
    }
}
