package com.menta.physical.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.physical.application.dto.RateLimitDimension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Locks the rate-limit log line and counter shape (ADR-0044); expectations are literals. */
class LogAndMetricCheckInRateLimitRejectionAdapterTest {

    private static final String COUNTER = "physical.checkin.rate.limited";

    private Logger logger;
    private ListAppender<ILoggingEvent> events;
    private SimpleMeterRegistry registry;
    private LogAndMetricCheckInRateLimitRejectionAdapter adapter;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogAndMetricCheckInRateLimitRejectionAdapter.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        registry = new SimpleMeterRegistry();
        adapter = new LogAndMetricCheckInRateLimitRejectionAdapter(registry);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(events);
    }

    private double count(String dimension) {
        Counter counter = registry.find(COUNTER).tag("dimension", dimension).counter();
        assertThat(counter).as("counter for dimension " + dimension).isNotNull();
        return counter.count();
    }

    @Test
    void exposed_contract_constants_equal_the_locked_literals() {
        assertThat(LogAndMetricCheckInRateLimitRejectionAdapter.LOG_MARKER)
            .isEqualTo("alarm=physical_checkin_rate_limited");
        assertThat(LogAndMetricCheckInRateLimitRejectionAdapter.COUNTER_NAME).isEqualTo(COUNTER);
        assertThat(LogAndMetricCheckInRateLimitRejectionAdapter.METRIC_DIMENSION_TAG)
            .isEqualTo("dimension");
    }

    @Test
    void both_dimension_counters_are_pre_registered_at_zero() {
        assertThat(count("ip")).isZero();
        assertThat(count("device")).isZero();
    }

    @Test
    void reporting_increments_only_the_matching_counter() {
        adapter.report(RateLimitDimension.DEVICE);
        adapter.report(RateLimitDimension.DEVICE);

        assertThat(count("device")).isEqualTo(2.0);
        assertThat(count("ip")).isZero();
    }

    @Test
    void reporting_writes_one_warn_line_with_the_dimension_and_no_device_id() {
        adapter.report(RateLimitDimension.IP);

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
            .isEqualTo("alarm=physical_checkin_rate_limited dimension=ip");
    }
}
