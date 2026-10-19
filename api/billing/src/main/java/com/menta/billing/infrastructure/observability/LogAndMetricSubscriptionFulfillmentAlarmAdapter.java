package com.menta.billing.infrastructure.observability;

import com.menta.billing.application.dto.SubscriptionFulfillmentAlarm;
import com.menta.billing.application.dto.SubscriptionFulfillmentAlarmReason;
import com.menta.billing.application.port.out.SubscriptionFulfillmentAlarmPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes the locked alarm log line and increments the alarm counter (#236).
 *
 * <p>The log goes first: it is the signal the Grafana rule consumes, so it must survive a counter
 * fault. Nothing is caught here; SLF4J and an in-memory counter do not throw.</p>
 */
@Slf4j
@Component
public class LogAndMetricSubscriptionFulfillmentAlarmAdapter
    implements SubscriptionFulfillmentAlarmPort {

    /**
     * Fixed prefix of every alarm line. The Grafana LogQL rule matches on it ({@code |=}), so
     * changing it silently disables the alert.
     */
    public static final String LOG_MARKER = "alarm=virtual_subscription_fulfillment";

    /** Value written for {@code subscriptionId} when there is no subscription row. */
    static final String NO_SUBSCRIPTION = "none";

    /** Counter name. The only tag is {@link #METRIC_REASON_TAG}; ids are never tags. */
    public static final String COUNTER_NAME = "billing.subscription.fulfillment.alarm";

    public static final String METRIC_REASON_TAG = "reason";

    private final Map<SubscriptionFulfillmentAlarmReason, Counter> counters =
        new EnumMap<>(SubscriptionFulfillmentAlarmReason.class);

    /** Pre-registers both reason counters at 0 so the meter is discoverable before an alarm. */
    public LogAndMetricSubscriptionFulfillmentAlarmAdapter(MeterRegistry meterRegistry) {
        for (var reason : SubscriptionFulfillmentAlarmReason.values()) {
            counters.put(
                reason,
                Counter.builder(COUNTER_NAME)
                    .tag(METRIC_REASON_TAG, reason.code())
                    .register(meterRegistry)
            );
        }
    }

    @Override
    public void raise(SubscriptionFulfillmentAlarm alarm) {
        log.error(
            LOG_MARKER + " reason={} paymentId={} subscriptionId={} planId={} userId={}",
            alarm.reason().code(),
            alarm.paymentId(),
            alarm.subscriptionId() == null ? NO_SUBSCRIPTION : alarm.subscriptionId(),
            alarm.planId(),
            alarm.userId()
        );
        counters.get(alarm.reason()).increment();
    }
}
