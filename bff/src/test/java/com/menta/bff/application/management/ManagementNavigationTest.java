package com.menta.bff.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests of {@link ManagementNavigation}: which management sections each role is offered (#354). */
@DisplayName("ManagementNavigation")
class ManagementNavigationTest {

    private static ManagementSection section(String key, Set<String> roles, boolean enabled) {
        return new ManagementSection(key, "Label " + key, "/gestion/" + key, roles, enabled);
    }

    private static List<String> keys(List<ManagementSection> sections) {
        return sections.stream().map(ManagementSection::key).toList();
    }

    private final ManagementNavigation navigation = new ManagementNavigation(List.of(
            section("content", Set.of("ADMIN", "INSTRUCTOR"), true),
            section("reception", Set.of("RECEPTIONIST", "ADMIN"), true),
            section("billing", Set.of("ADMIN"), true),
            section("devices", Set.of("ADMIN"), false)));

    @Test
    @DisplayName("offers only the enabled sections whose roles include the user's role")
    void filtersByRole() {
        assertThat(keys(navigation.sectionsFor(List.of("INSTRUCTOR")))).containsExactly("content");
        assertThat(keys(navigation.sectionsFor(List.of("RECEPTIONIST")))).containsExactly("reception");
    }

    @Test
    @DisplayName("an administrator sees every enabled section, in the declared order")
    void keepsDeclaredOrder() {
        assertThat(keys(navigation.sectionsFor(List.of("ADMIN"))))
                .containsExactly("content", "reception", "billing");
    }

    @Test
    @DisplayName("a disabled section is never offered, even to a role it lists")
    void skipsDisabledSections() {
        assertThat(keys(navigation.sectionsFor(List.of("ADMIN")))).doesNotContain("devices");
    }

    @Test
    @DisplayName("several roles add up without repeating a section")
    void multipleRolesDoNotDuplicate() {
        assertThat(keys(navigation.sectionsFor(List.of("INSTRUCTOR", "RECEPTIONIST", "ADMIN"))))
                .containsExactly("content", "reception", "billing");
        assertThat(keys(navigation.sectionsFor(List.of("INSTRUCTOR", "RECEPTIONIST"))))
                .containsExactly("content", "reception");
    }

    @Test
    @DisplayName("a role with no sections, an unknown role, an empty or a null set of roles yields nothing")
    void unknownOrEmptyRolesSeeNothing() {
        assertThat(navigation.sectionsFor(List.of("STUDENT"))).isEmpty();
        assertThat(navigation.sectionsFor(List.of("SUPERUSER"))).isEmpty();
        assertThat(navigation.sectionsFor(List.of())).isEmpty();
        assertThat(navigation.sectionsFor(null)).isEmpty();
    }

    @Test
    @DisplayName("the ROLE_ prefix Spring Security adds is ignored")
    void normalizesTheRolePrefix() {
        assertThat(keys(navigation.sectionsFor(List.of("ROLE_INSTRUCTOR")))).containsExactly("content");
        assertThat(keys(navigation.sectionsFor(List.of("ROLE_ADMIN", "RECEPTIONIST"))))
                .containsExactly("content", "reception", "billing");
    }

    @Test
    @DisplayName("an empty catalog offers nothing")
    void emptyCatalog() {
        assertThat(new ManagementNavigation(List.of()).sectionsFor(List.of("ADMIN"))).isEmpty();
    }

    @Test
    @DisplayName("the returned list cannot be used to change the catalog")
    void resultIsImmutable() {
        List<ManagementSection> result = navigation.sectionsFor(List.of("ADMIN"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
