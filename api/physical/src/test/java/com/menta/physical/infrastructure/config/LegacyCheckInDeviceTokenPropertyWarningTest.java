package com.menta.physical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * Locks the one-time retirement warning (#266, D3). The property name is a literal on purpose:
 * renaming the retired key must fail here, and the secret value must never reach the log.
 */
class LegacyCheckInDeviceTokenPropertyWarningTest {

    private static final String PROPERTY = "app.physical.checkin.device-token";
    private static final String SECRET_VALUE = "legacy-shared-secret-value";

    private Logger logger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LegacyCheckInDeviceTokenPropertyWarning.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(events);
    }

    @Test
    void warns_exactly_once_naming_the_property_when_it_is_still_configured() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROPERTY, SECRET_VALUE);

        new LegacyCheckInDeviceTokenPropertyWarning(environment).warnIfPresent();

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains(PROPERTY);
    }

    @Test
    void never_logs_the_configured_value() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROPERTY, SECRET_VALUE);

        new LegacyCheckInDeviceTokenPropertyWarning(environment).warnIfPresent();

        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getFormattedMessage()).doesNotContain(SECRET_VALUE);
        assertThat(event.getMessage()).doesNotContain(SECRET_VALUE);
        assertThat(event.getArgumentArray()).containsExactly(PROPERTY);
    }

    @Test
    void warns_in_a_production_profile_too_and_never_fails_the_startup() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROPERTY, SECRET_VALUE);
        environment.setActiveProfiles("prod");

        new LegacyCheckInDeviceTokenPropertyWarning(environment).warnIfPresent();

        assertThat(events.list).hasSize(1);
        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void stays_silent_when_the_property_is_not_configured() {
        MockEnvironment environment = new MockEnvironment().withProperty("unrelated.key", "x");

        new LegacyCheckInDeviceTokenPropertyWarning(environment).warnIfPresent();

        assertThat(events.list).isEmpty();
    }
}
