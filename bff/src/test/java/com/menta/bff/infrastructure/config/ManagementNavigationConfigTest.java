package com.menta.bff.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.management.ManagementNavigation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The production catalog of management sections (#354). */
@DisplayName("ManagementNavigationConfig")
class ManagementNavigationConfigTest {

    private final ManagementNavigation navigation = new ManagementNavigationConfig().managementNavigation();

    @Test
    @DisplayName("no section is offered yet: none has a screen, so none may be a link")
    void everySectionStartsDisabled() {
        for (String role : List.of("ADMIN", "INSTRUCTOR", "RECEPTIONIST", "STUDENT")) {
            assertThat(navigation.sectionsFor(List.of("ROLE_" + role))).as(role).isEmpty();
        }
    }
}
