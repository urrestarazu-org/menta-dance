package com.menta.physical.infrastructure.observability;

import static com.menta.physical.application.dto.DeviceAuthenticationRejectionReason.EXPIRED;
import static com.menta.physical.application.dto.DeviceAuthenticationRejectionReason.REVOKED;
import static com.menta.physical.application.dto.DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.physical.application.dto.DeviceAuthenticationRejection;
import com.menta.physical.application.dto.DeviceAuthenticationRejectionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Locks the rejection log line and the counter shape (#266). The expected strings below are
 * deliberately literals, not the adapter's own constants, so any drift in the contract fails here.
 */
class LogAndMetricDeviceAuthenticationRejectionAdapterTest {

    private static final String COUNTER = "physical.checkin.device.rejected";
    private static final UUID DEVICE_ID = UUID.fromString("0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a30");

    private Logger logger;
    private ListAppender<ILoggingEvent> events;
    private SimpleMeterRegistry registry;
    private LogAndMetricDeviceAuthenticationRejectionAdapter adapter;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(
            LogAndMetricDeviceAuthenticationRejectionAdapter.class
        );
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        registry = new SimpleMeterRegistry();
        adapter = new LogAndMetricDeviceAuthenticationRejectionAdapter(registry);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(events);
    }

    private static DeviceAuthenticationRejection rejection(
        DeviceAuthenticationRejectionReason reason, UUID deviceId
    ) {
        return new DeviceAuthenticationRejection(reason, deviceId);
    }

    private double count(String reason) {
        Counter counter = registry.find(COUNTER).tag("reason", reason).counter();
        assertThat(counter).as("counter for reason " + reason).isNotNull();
        return counter.count();
    }

    // --- exposed contract constants ---

    @Test
    void exposed_contract_constants_equal_the_locked_literals() {
        assertThat(LogAndMetricDeviceAuthenticationRejectionAdapter.LOG_MARKER)
            .isEqualTo("alarm=physical_checkin_device_rejected");
        assertThat(LogAndMetricDeviceAuthenticationRejectionAdapter.COUNTER_NAME)
            .isEqualTo(COUNTER);
        assertThat(LogAndMetricDeviceAuthenticationRejectionAdapter.METRIC_REASON_TAG)
            .isEqualTo("reason");
    }

    @Test
    void the_reason_codes_equal_the_locked_tag_values() {
        assertThat(UNKNOWN_OR_INVALID.code()).isEqualTo("unknown_or_invalid");
        assertThat(REVOKED.code()).isEqualTo("revoked");
        assertThat(EXPIRED.code()).isEqualTo("expired");
    }

    // --- log contract ---

    @Test
    void revoked_writes_the_exact_contract_line_at_warn_level() {
        adapter.report(rejection(REVOKED, DEVICE_ID));

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo(
            "alarm=physical_checkin_device_rejected reason=revoked"
                + " deviceId=0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a30"
        );
    }

    @Test
    void an_absent_device_id_is_written_as_none() {
        adapter.report(rejection(UNKNOWN_OR_INVALID, null));

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo(
            "alarm=physical_checkin_device_rejected reason=unknown_or_invalid deviceId=none"
        );
    }

    // --- metric contract ---

    @Test
    void all_reason_counters_are_pre_registered_at_zero() {
        assertThat(count("unknown_or_invalid")).isZero();
        assertThat(count("revoked")).isZero();
        assertThat(count("expired")).isZero();
    }

    @Test
    void reporting_increments_only_the_matching_counter_and_tags_only_the_reason() {
        adapter.report(rejection(EXPIRED, DEVICE_ID));

        assertThat(count("expired")).isEqualTo(1.0);
        assertThat(count("revoked")).isZero();
        assertThat(count("unknown_or_invalid")).isZero();
        List<Tag> tags = registry.find(COUNTER).tag("reason", "expired")
            .counter().getId().getTags();
        assertThat(tags).extracting(Tag::getKey).containsExactly("reason");
    }

    @Test
    void every_occurrence_writes_one_line_and_increments_again_with_no_deduplication() {
        adapter.report(rejection(UNKNOWN_OR_INVALID, null));
        adapter.report(rejection(UNKNOWN_OR_INVALID, null));

        assertThat(count("unknown_or_invalid")).isEqualTo(2.0);
        assertThat(count("revoked")).isZero();
        assertThat(count("expired")).isZero();
        assertThat(events.list).hasSize(2);
    }

    @Test
    void the_log_reason_and_the_metric_reason_are_the_same_value_for_each_case() {
        for (var reason : DeviceAuthenticationRejectionReason.values()) {
            adapter.report(rejection(reason, DEVICE_ID));
        }

        assertThat(events.list).hasSize(3);
        for (var reason : DeviceAuthenticationRejectionReason.values()) {
            String logged = events.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(" reason=" + reason.code() + " "))
                .findFirst()
                .orElseThrow();
            assertThat(logged)
                .startsWith("alarm=physical_checkin_device_rejected reason=" + reason.code());
            assertThat(count(reason.code())).isEqualTo(1.0);
        }
    }
}
