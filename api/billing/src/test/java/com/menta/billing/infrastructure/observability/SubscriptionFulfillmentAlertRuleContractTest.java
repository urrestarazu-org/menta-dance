package com.menta.billing.infrastructure.observability;

import static com.menta.billing.infrastructure.observability.LogAndMetricSubscriptionFulfillmentAlarmAdapter.COUNTER_NAME;
import static com.menta.billing.infrastructure.observability.LogAndMetricSubscriptionFulfillmentAlarmAdapter.LOG_MARKER;
import static com.menta.billing.infrastructure.observability.LogAndMetricSubscriptionFulfillmentAlarmAdapter.METRIC_REASON_TAG;
import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.application.dto.SubscriptionFulfillmentAlarmReason;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the provisioned Grafana alert rule (#236) to the Java side of the alarm contract. The rule
 * is a LogQL query over the line written by {@link
 * LogAndMetricSubscriptionFulfillmentAlarmAdapter}, so renaming the log marker, the {@code reason}
 * key or a reason code without updating the YAML silently disables the alert. These tests turn
 * that drift into a build failure.
 *
 * <p>The rule file lives at the repository root, outside any Gradle module. Gradle runs module
 * tests with the module directory as the working directory, so the root is two levels up.</p>
 */
class SubscriptionFulfillmentAlertRuleContractTest {

    private static final Path REPOSITORY_ROOT =
        Path.of("").toAbsolutePath().getParent().getParent();
    private static final Path RULE_FILE = REPOSITORY_ROOT.resolve(
        "observability/grafana/provisioning/alerting/billing-subscription-fulfillment.yml"
    );
    private static final Path LOKI_DATASOURCE_FILE = REPOSITORY_ROOT.resolve(
        "observability/grafana/provisioning/datasources/loki.yml"
    );

    private static String rule;
    private static String lokiDatasource;

    @BeforeAll
    static void loadFiles() throws IOException {
        assertThat(RULE_FILE).as("alert rule file").isRegularFile();
        assertThat(LOKI_DATASOURCE_FILE).as("loki datasource file").isRegularFile();
        rule = Files.readString(RULE_FILE);
        lokiDatasource = Files.readString(LOKI_DATASOURCE_FILE);
    }

    @Test
    void query_matches_the_adapter_log_marker() {
        assertThat(rule).contains("|= \"" + LOG_MARKER + "\"");
    }

    @Test
    void query_extracts_and_groups_by_the_reason_key_used_in_the_log_line() {
        assertThat(rule)
            .contains("| logfmt " + METRIC_REASON_TAG + " [")
            .contains("sum by (" + METRIC_REASON_TAG + ")");
    }

    @Test
    void query_selects_the_api_service_stream_with_an_instant_loki_query() {
        assertThat(rule)
            .contains("{service_name=\"menta-dance-api\"}")
            .contains("queryType: instant");
    }

    @ParameterizedTest
    @EnumSource(SubscriptionFulfillmentAlarmReason.class)
    void rule_documents_every_reason_code(SubscriptionFulfillmentAlarmReason reason) {
        assertThat(rule).contains(reason.code());
    }

    @Test
    void rule_is_log_based_and_does_not_depend_on_the_counter_or_prometheus() {
        assertThat(rule)
            .doesNotContain(COUNTER_NAME)
            .doesNotContainIgnoringCase("prometheus");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "apiVersion: 1",
        "name: billing",
        "uid: billing-subscription-fulfillment-alarm",
        "interval: 1m",
        "for: 0s",
        "noDataState: OK",
        "execErrState: Error",
        "condition: C",
        "severity: critical",
        "module: billing",
        "datasourceUid: loki"
    })
    void rule_declares_contract_setting(String setting) {
        assertThat(rule).contains(setting);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contactPoint", "contact_point", "notification_settings", "receiver"})
    void rule_defines_no_contact_point_or_notification_routing(String forbidden) {
        assertThat(rule).doesNotContainIgnoringCase(forbidden);
    }

    @Test
    void loki_datasource_exposes_the_uid_the_rule_references() {
        assertThat(lokiDatasource).contains("uid: loki");
    }
}
