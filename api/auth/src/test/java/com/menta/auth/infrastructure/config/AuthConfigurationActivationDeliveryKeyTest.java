package com.menta.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class AuthConfigurationActivationDeliveryKeyTest {

    private static final String DEV_JWT_KEY =
        "ZGV2LW9ubHktc2VjcmV0LXdpdGgtMzItYnl0ZXMtbWluaW11bS0zMmJ5dGVzLW1pbmltdW0tMzJieXRlcw==";
    private static final String DEV_DELIVERY_KEY =
        "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";

    @Test
    void rejects_the_dev_delivery_key_in_a_production_profile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        AuthConfiguration configuration = new AuthConfiguration(environment);
        ReflectionTestUtils.setField(configuration, "jwtBase64Secret", DEV_JWT_KEY);
        ReflectionTestUtils.setField(configuration, "activationDeliveryKey", DEV_DELIVERY_KEY);

        assertThatThrownBy(configuration::validateSecretNotDefaultInProduction)
            .isInstanceOf(IllegalStateException.class);
    }
}
