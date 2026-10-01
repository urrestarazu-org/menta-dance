package com.menta.billing.infrastructure.observability;

import static com.menta.billing.application.dto.SubscriptionFulfillmentAlarmReason.PLAN_MISSING;
import static com.menta.billing.application.dto.SubscriptionFulfillmentAlarmReason.SUBSCRIPTION_MISSING;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.billing.application.dto.SubscriptionFulfillmentAlarm;
import com.menta.billing.application.dto.SubscriptionFulfillmentAlarmReason;
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
 * Locks the alarm log line (consumed by the Grafana LogQL rule) and the counter shape. The
 * expected strings below are deliberately literals, not the adapter's own constants, so any drift
 * in the contract fails here.
 */
class LogAndMetricSubscriptionFulfillmentAlarmAdapterTest {

    private static final String COUNTER = "billing.subscription.fulfillment.alarm";
    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String PLAN_ID = "44444444-4444-4444-4444-444444444444";

    private Logger logger;
    private ListAppender<ILoggingEvent> events;
    private SimpleMeterRegistry registry;
    private LogAndMetricSubscriptionFulfillmentAlarmAdapter adapter;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(
            LogAndMetricSubscriptionFulfillmentAlarmAdapter.class
        );
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        registry = new SimpleMeterRegistry();
        adapter = new LogAndMetricSubscriptionFulfillmentAlarmAdapter(registry);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(events);
    }

    private static SubscriptionFulfillmentAlarm alarm(
        SubscriptionFulfillmentAlarmReason reason, UUID subscriptionId
    ) {
        return new SubscriptionFulfillmentAlarm(
            reason, PAYMENT_ID, subscriptionId, PLAN_ID, USER_ID
        );
    }

    private double count(String reason) {
        Counter counter = registry.find(COUNTER).tag("reason", reason).counter();
        assertThat(counter).as("counter for reason " + reason).isNotNull();
        return counter.count();
    }

    // --- exposed contract constants (referenced by the Grafana rule contract test) ---

    @Test
    void exposed_contract_constants_equal_the_locked_literals() {
        assertThat(LogAndMetricSubscriptionFulfillmentAlarmAdapter.LOG_MARKER)
            .isEqualTo("alarm=virtual_subscription_fulfillment");
        assertThat(LogAndMetricSubscriptionFulfillmentAlarmAdapter.COUNTER_NAME)
            .isEqualTo(COUNTER);
        assertThat(LogAndMetricSubscriptionFulfillmentAlarmAdapter.METRIC_REASON_TAG)
            .isEqualTo("reason");
    }

    // --- log contract ---

    @Test
    void plan_missing_writes_the_exact_contract_line_at_error_level() {
        adapter.raise(alarm(PLAN_MISSING, SUB_ID));

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo(
            "alarm=virtual_subscription_fulfillment reason=plan_missing"
                + " paymentId=11111111-1111-1111-1111-111111111111"
                + " subscriptionId=22222222-2222-2222-2222-222222222222"
                + " planId=44444444-4444-4444-4444-444444444444"
                + " userId=33333333-3333-3333-3333-333333333333"
        );
    }

    @Test
    void subscription_missing_writes_none_for_the_absent_subscription_id() {
        adapter.raise(alarm(SUBSCRIPTION_MISSING, null));

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo(
            "alarm=virtual_subscription_fulfillment reason=subscription_missing"
                + " paymentId=11111111-1111-1111-1111-111111111111"
                + " subscriptionId=none"
                + " planId=44444444-4444-4444-4444-444444444444"
                + " userId=33333333-3333-3333-3333-333333333333"
        );
    }

    // --- metric contract ---

    @Test
    void both_reason_counters_are_pre_registered_at_zero() {
        assertThat(count("plan_missing")).isZero();
        assertThat(count("subscription_missing")).isZero();
    }

    @Test
    void raising_increments_only_the_matching_counter_and_tags_only_the_reason() {
        adapter.raise(alarm(PLAN_MISSING, SUB_ID));

        assertThat(count("plan_missing")).isEqualTo(1.0);
        assertThat(count("subscription_missing")).isZero();
        List<Tag> tags = registry.find(COUNTER).tag("reason", "plan_missing")
            .counter().getId().getTags();
        assertThat(tags).extracting(Tag::getKey).containsExactly("reason");
    }

    @Test
    void every_occurrence_increments_the_counter_again_with_no_deduplication() {
        adapter.raise(alarm(SUBSCRIPTION_MISSING, null));
        adapter.raise(alarm(SUBSCRIPTION_MISSING, null));

        assertThat(count("subscription_missing")).isEqualTo(2.0);
        assertThat(count("plan_missing")).isZero();
        assertThat(events.list).hasSize(2);
    }

    @Test
    void the_log_reason_and_the_metric_reason_are_the_same_value_for_each_case() {
        for (var reason : SubscriptionFulfillmentAlarmReason.values()) {
            adapter.raise(alarm(reason, null));
        }

        assertThat(events.list).hasSize(2);
        for (var reason : SubscriptionFulfillmentAlarmReason.values()) {
            String logged = events.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(" reason=" + reason.code() + " "))
                .findFirst()
                .orElseThrow();
            assertThat(logged)
                .startsWith("alarm=virtual_subscription_fulfillment reason=" + reason.code());
            assertThat(count(reason.code())).isEqualTo(1.0);
        }
        assertThat(PLAN_MISSING.code()).isNotEqualTo(SUBSCRIPTION_MISSING.code());
    }
}
