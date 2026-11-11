package com.menta.bff.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.management.ManagementNavigation;
import com.menta.bff.application.management.ManagementSection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The production catalog of management sections (#354). */
@DisplayName("ManagementNavigationConfig")
class ManagementNavigationConfigTest {

    private final ManagementNavigation navigation = new ManagementNavigationConfig().managementNavigation();

    private List<String> keysFor(String role) {
        return navigation.sectionsFor(List.of("ROLE_" + role)).stream().map(ManagementSection::key).toList();
    }

    @Test
    @DisplayName("only the sections that have a screen are offered: today, Usuarios and only to an administrator")
    void onlyUsersIsOffered() {
        assertThat(keysFor("ADMIN")).containsExactly("users");
        for (String role : List.of("INSTRUCTOR", "RECEPTIONIST", "STUDENT")) {
            assertThat(keysFor(role)).as(role).isEmpty();
        }
    }

    @Test
    @DisplayName("the Usuarios section points at the screen the controller serves")
    void usersSectionPoints() {
        ManagementSection users = navigation.sectionsFor(List.of("ROLE_ADMIN")).get(0);

        assertThat(users.label()).isEqualTo("Usuarios");
        assertThat(users.path()).isEqualTo("/gestion/usuarios");
        assertThat(users.roles()).containsExactly("ADMIN");
    }
}
