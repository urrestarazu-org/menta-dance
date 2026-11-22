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

    private ManagementSection sectionFor(String role, String key) {
        return navigation.sectionsFor(List.of("ROLE_" + role)).stream()
                .filter(section -> section.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("only sections with a screen are offered (courses to admin and instructor, Recepción to reception "
            + "and admin, Pagos, Compras con excepción and Usuarios to admin)")
    void onlySectionsWithAScreenAreOffered() {
        assertThat(keysFor("ADMIN")).containsExactlyInAnyOrder("cursos-presenciales", "cursos-virtuales", "reception",
                "payments", "exception-purchases", "users");
        assertThat(keysFor("INSTRUCTOR")).containsExactlyInAnyOrder("cursos-presenciales", "cursos-virtuales");
        assertThat(keysFor("RECEPTIONIST")).containsExactly("reception");
        assertThat(keysFor("STUDENT")).isEmpty();
    }

    @Test
    @DisplayName("the Cursos presenciales section points at the screen the controller serves, and the disabled "
            + "Sesiones entry is a different one")
    void coursesSectionPoints() {
        ManagementSection courses = sectionFor("INSTRUCTOR", "cursos-presenciales");

        assertThat(courses.label()).isEqualTo("Cursos presenciales");
        assertThat(courses.path()).isEqualTo("/gestion/cursos-presenciales");
        assertThat(courses.roles()).containsExactlyInAnyOrder("ADMIN", "INSTRUCTOR");
        assertThat(keysFor("ADMIN")).doesNotContain("sessions");
    }

    @Test
    @DisplayName("the Cursos virtuales section points at the screen the controller serves, for admin and instructor, "
            + "and the disabled Contenido entry is a different one")
    void virtualCoursesSectionPoints() {
        ManagementSection courses = sectionFor("INSTRUCTOR", "cursos-virtuales");

        assertThat(courses.label()).isEqualTo("Cursos virtuales");
        assertThat(courses.path()).isEqualTo("/gestion/cursos-virtuales");
        assertThat(courses.roles()).containsExactlyInAnyOrder("ADMIN", "INSTRUCTOR");
        assertThat(keysFor("ADMIN")).doesNotContain("content");
    }

    @Test
    @DisplayName("the Usuarios section points at the screen the controller serves")
    void usersSectionPoints() {
        ManagementSection users = sectionFor("ADMIN", "users");

        assertThat(users.label()).isEqualTo("Usuarios");
        assertThat(users.path()).isEqualTo("/gestion/usuarios");
        assertThat(users.roles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("the Pagos section points at the screen the controller serves, for admin only, and replaces the "
            + "old disabled Facturación one")
    void paymentsSectionPoints() {
        ManagementSection payments = sectionFor("ADMIN", "payments");

        assertThat(payments.label()).isEqualTo("Pagos");
        assertThat(payments.path()).isEqualTo("/gestion/pagos");
        assertThat(payments.roles()).containsExactly("ADMIN");
        assertThat(keysFor("ADMIN")).doesNotContain("billing");
    }

    @Test
    @DisplayName("the Compras con excepción section points at the screen the controller serves, for admin only")
    void exceptionPurchasesSectionPoints() {
        ManagementSection purchases = sectionFor("ADMIN", "exception-purchases");

        assertThat(purchases.label()).isEqualTo("Compras con excepción");
        assertThat(purchases.path()).isEqualTo("/gestion/compras-con-excepcion");
        assertThat(purchases.roles()).containsExactly("ADMIN");
        assertThat(keysFor("INSTRUCTOR")).doesNotContain("exception-purchases");
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
