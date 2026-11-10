package com.menta.bff.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests of {@link ManagementRoles}: the single place roles are normalized and classified (#354). */
@DisplayName("ManagementRoles")
class ManagementRolesTest {

    @Test
    @DisplayName("normalize strips the ROLE_ prefix and drops blanks and nulls")
    void normalize() {
        assertThat(ManagementRoles.normalize(Arrays.asList("ROLE_ADMIN", "STUDENT", " ", "", null, "ROLE_")))
                .containsExactlyInAnyOrder("ADMIN", "STUDENT");
    }

    @Test
    @DisplayName("normalize of null is empty")
    void normalizeNull() {
        assertThat(ManagementRoles.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("staff are the administrator, the instructor and the receptionist")
    void staffRoles() {
        assertThat(ManagementRoles.isStaff(List.of("ROLE_ADMIN"))).isTrue();
        assertThat(ManagementRoles.isStaff(List.of("INSTRUCTOR"))).isTrue();
        assertThat(ManagementRoles.isStaff(List.of("ROLE_RECEPTIONIST"))).isTrue();
        assertThat(ManagementRoles.isStaff(List.of("STUDENT", "ROLE_INSTRUCTOR"))).isTrue();
    }

    @Test
    @DisplayName("students, unknown roles and no roles are not staff")
    void nonStaff() {
        assertThat(ManagementRoles.isStaff(List.of("ROLE_STUDENT"))).isFalse();
        assertThat(ManagementRoles.isStaff(List.of("SUPERUSER"))).isFalse();
        assertThat(ManagementRoles.isStaff(List.of())).isFalse();
        assertThat(ManagementRoles.isStaff(null)).isFalse();
    }
}
