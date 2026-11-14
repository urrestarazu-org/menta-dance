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
    @DisplayName("only sections with a screen are offered (courses to admin and instructor, Recepción to reception "
            + "and admin, Usuarios to admin)")
    void onlySectionsWithAScreenAreOffered() {
        assertThat(keysFor("ADMIN")).containsExactly("cursos-presenciales", "reception", "users");
        assertThat(keysFor("INSTRUCTOR")).containsExactly("cursos-presenciales");
        assertThat(keysFor("RECEPTIONIST")).containsExactly("reception");
        assertThat(keysFor("STUDENT")).isEmpty();
    }

    @Test
    @DisplayName("the Cursos presenciales section points at the screen the controller serves, and the disabled "
            + "Sesiones entry is a different one")
    void coursesSectionPoints() {
        ManagementSection courses = navigation.sectionsFor(List.of("ROLE_INSTRUCTOR")).get(0);

        assertThat(courses.label()).isEqualTo("Cursos presenciales");
        assertThat(courses.path()).isEqualTo("/gestion/cursos-presenciales");
        assertThat(courses.roles()).containsExactlyInAnyOrder("ADMIN", "INSTRUCTOR");
        assertThat(keysFor("ADMIN")).doesNotContain("sessions");
    }

    @Test
    @DisplayName("the Usuarios section points at the screen the controller serves")
    void usersSectionPoints() {
        ManagementSection users = navigation.sectionsFor(List.of("ROLE_ADMIN")).get(2);

        assertThat(users.label()).isEqualTo("Usuarios");
        assertThat(users.path()).isEqualTo("/gestion/usuarios");
        assertThat(users.roles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("the Recepción section points at the screen the controller serves, for reception and admin only")
    void receptionSectionPoints() {
        ManagementSection reception = navigation.sectionsFor(List.of("ROLE_RECEPTIONIST")).get(0);

        assertThat(reception.label()).isEqualTo("Recepción");
        assertThat(reception.path()).isEqualTo("/gestion/recepcion");
        assertThat(reception.roles()).containsExactlyInAnyOrder("RECEPTIONIST", "ADMIN");
    }
}
