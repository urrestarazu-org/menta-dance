package com.menta.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class DemoProfileGuardTest {

    @ParameterizedTest
    @CsvSource({"demo,prod", "DEMO,Production", "demo,staging", "Demo,PROD"})
    void aborts_when_demo_is_combined_with_a_production_like_profile(String demo, String production) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(demo, production);

        assertThatThrownBy(() -> new DemoProfileGuard(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("demo");
    }

    @Test
    void accepts_demo_alone() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");

        assertThatCode(() -> new DemoProfileGuard(environment)).doesNotThrowAnyException();
    }

    @Test
    void accepts_a_production_profile_without_demo() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new DemoProfileGuard(environment)).doesNotThrowAnyException();
    }

    @Test
    void accepts_no_active_profiles() {
        assertThat(new DemoProfileGuard(new MockEnvironment())).isNotNull();
    }
}
