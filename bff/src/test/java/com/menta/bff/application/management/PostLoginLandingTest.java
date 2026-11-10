package com.menta.bff.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests of {@link PostLoginLanding}: where each role lands after signing in (#354). */
@DisplayName("PostLoginLanding")
class PostLoginLandingTest {

    @ParameterizedTest(name = "{0} lands on the management home")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST", "ROLE_ADMIN", "ROLE_INSTRUCTOR",
        "ROLE_RECEPTIONIST"})
    @DisplayName("staff land on the management home")
    void staffLandOnManagement(String role) {
        assertThat(PostLoginLanding.pathFor(List.of(role))).isEqualTo("/gestion");
    }

    @Test
    @DisplayName("students land on the dashboard")
    void studentsLandOnDashboard() {
        assertThat(PostLoginLanding.pathFor(List.of("ROLE_STUDENT"))).isEqualTo("/dashboard");
    }

    @Test
    @DisplayName("an unknown role, no roles or null land on the dashboard")
    void everyoneElseLandsOnDashboard() {
        assertThat(PostLoginLanding.pathFor(List.of("SUPERUSER"))).isEqualTo("/dashboard");
        assertThat(PostLoginLanding.pathFor(List.of())).isEqualTo("/dashboard");
        assertThat(PostLoginLanding.pathFor(null)).isEqualTo("/dashboard");
    }

    @Test
    @DisplayName("a staff role wins over a student role when both are present")
    void staffWinsOverStudent() {
        assertThat(PostLoginLanding.pathFor(List.of("STUDENT", "ROLE_INSTRUCTOR"))).isEqualTo("/gestion");
    }
}
