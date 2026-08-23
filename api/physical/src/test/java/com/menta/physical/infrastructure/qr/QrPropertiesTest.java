package com.menta.physical.infrastructure.qr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class QrPropertiesTest {

    @Test
    void defaults_match_the_configured_check_in_flow_values() {
        QrProperties properties = new QrProperties();

        assertThat(properties.getSessionWindowBefore()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getSessionWindowAfter()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.getCredentialTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getLockTtl()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void every_value_is_overridable() {
        QrProperties properties = new QrProperties();

        properties.setSessionWindowBefore(Duration.ofMinutes(5));
        properties.setSessionWindowAfter(Duration.ofMinutes(45));
        properties.setCredentialTtl(Duration.ofSeconds(15));
        properties.setLockTtl(Duration.ofSeconds(3));

        assertThat(properties.getSessionWindowBefore()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getSessionWindowAfter()).isEqualTo(Duration.ofMinutes(45));
        assertThat(properties.getCredentialTtl()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getLockTtl()).isEqualTo(Duration.ofSeconds(3));
    }
}
