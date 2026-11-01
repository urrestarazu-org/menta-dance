package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the provisioned Grafana rule (#672) to the log line written by {@link
 * OutboxReconciliationWorker}. The rule is a LogQL query over that line, so renaming the marker or
 * the {@code eventType} key without updating the YAML silently disables the alert.
 *
 * <p>Gradle runs module tests with the module directory as the working directory, so the
 * repository root is two levels up.</p>
 */
class AuthOutboxDeadAlertRuleContractTest {

    private static final Path RULE_FILE = Path.of("").toAbsolutePath().getParent().getParent()
        .resolve("observability/grafana/provisioning/alerting/auth-outbox-dead.yml");

    private static String rule;

    @BeforeAll
    static void loadRule() throws IOException {
        assertThat(RULE_FILE).as("alert rule file").isRegularFile();
        rule = Files.readString(RULE_FILE);
    }

    @Test
    void query_matches_the_worker_dead_alarm_marker() {
        assertThat(rule).contains("|= \"" + OutboxReconciliationWorker.DEAD_ALARM_MARKER + "\"");
    }

    @Test
    void query_groups_by_the_event_type_key_used_in_the_log_line() {
        assertThat(rule)
            .contains("| logfmt eventType [")
            .contains("sum by (eventType)");
    }

    @Test
    void query_selects_the_api_service_stream_with_an_instant_loki_query() {
        assertThat(rule)
            .contains("{service_name=\"menta-dance-api\"}")
            .contains("queryType: instant");
    }
}
