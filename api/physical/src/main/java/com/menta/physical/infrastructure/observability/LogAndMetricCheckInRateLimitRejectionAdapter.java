package com.menta.physical.infrastructure.observability;

import com.menta.physical.application.dto.RateLimitDimension;
import com.menta.physical.application.port.out.CheckInRateLimitRejectionPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes the rate-limit log line and increments the rate-limit counter (ADR-0044).
 *
 * <p>The line carries the tripped dimension only: no device id, no client address. Logged at WARN
 * because a throttled reader is an operator signal, not a server fault. The counter is tagged by
 * dimension only; identifiers are never tags.</p>
 */
@Slf4j
@Component
public class LogAndMetricCheckInRateLimitRejectionAdapter
    implements CheckInRateLimitRejectionPort {

    /** Fixed prefix of every rate-limit line. Dashboards and alerts match on it. */
    public static final String LOG_MARKER = "alarm=physical_checkin_rate_limited";

    public static final String COUNTER_NAME = "physical.checkin.rate.limited";

    public static final String METRIC_DIMENSION_TAG = "dimension";

    private final Map<RateLimitDimension, Counter> counters =
        new EnumMap<>(RateLimitDimension.class);

    /** Pre-registers every dimension counter at 0 so the meter exists before a rejection. */
    public LogAndMetricCheckInRateLimitRejectionAdapter(MeterRegistry meterRegistry) {
        for (var dimension : RateLimitDimension.values()) {
            counters.put(
                dimension,
                Counter.builder(COUNTER_NAME)
                    .tag(METRIC_DIMENSION_TAG, dimension.tag())
                    .register(meterRegistry)
            );
        }
    }

    @Override
    public void report(RateLimitDimension dimension) {
        log.warn(LOG_MARKER + " dimension={}", dimension.tag());
        counters.get(dimension).increment();
    }
}
