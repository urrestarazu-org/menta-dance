package com.menta.physical.infrastructure.observability;

import com.menta.physical.application.dto.DeviceAuthenticationRejection;
import com.menta.physical.application.dto.DeviceAuthenticationRejectionReason;
import com.menta.physical.application.port.out.DeviceAuthenticationRejectionPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes the locked rejection log line and increments the rejection counter (#266).
 *
 * <p>The log goes first so it survives a counter fault. Nothing is caught here; SLF4J and an
 * in-memory counter do not throw. Logged at WARN: a rejected reader is an operator signal, not a
 * server fault.</p>
 */
@Slf4j
@Component
public class LogAndMetricDeviceAuthenticationRejectionAdapter
    implements DeviceAuthenticationRejectionPort {

    /** Fixed prefix of every rejection line. Dashboards and alerts match on it. */
    public static final String LOG_MARKER = "alarm=physical_checkin_device_rejected";

    /** Value written for {@code deviceId} when the submitted id is not a valid UUID. */
    static final String NO_DEVICE = "none";

    /** Counter name. The only tag is {@link #METRIC_REASON_TAG}; device ids are never tags. */
    public static final String COUNTER_NAME = "physical.checkin.device.rejected";

    public static final String METRIC_REASON_TAG = "reason";

    private final Map<DeviceAuthenticationRejectionReason, Counter> counters =
        new EnumMap<>(DeviceAuthenticationRejectionReason.class);

    /** Pre-registers every reason counter at 0 so the meter exists before a rejection. */
    public LogAndMetricDeviceAuthenticationRejectionAdapter(MeterRegistry meterRegistry) {
        for (var reason : DeviceAuthenticationRejectionReason.values()) {
            counters.put(
                reason,
                Counter.builder(COUNTER_NAME)
                    .tag(METRIC_REASON_TAG, reason.code())
                    .register(meterRegistry)
            );
        }
    }

    @Override
    public void report(DeviceAuthenticationRejection rejection) {
        log.warn(
            LOG_MARKER + " reason={} deviceId={}",
            rejection.reason().code(),
            rejection.deviceId() == null ? NO_DEVICE : rejection.deviceId()
        );
        counters.get(rejection.reason()).increment();
    }
}
