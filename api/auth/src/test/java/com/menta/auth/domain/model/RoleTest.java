package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void receptionist_is_appended_right_after_student() {
        Role[] values = Role.values();

        assertThat(values).contains(Role.RECEPTIONIST);
        int studentIndex = indexOf(values, Role.STUDENT);
        int receptionistIndex = indexOf(values, Role.RECEPTIONIST);
        assertThat(receptionistIndex).isEqualTo(studentIndex + 1);
    }

    @Test
    void receptionist_round_trips_through_value_of() {
        assertThat(Role.valueOf("RECEPTIONIST")).isEqualTo(Role.RECEPTIONIST);
    }

    private static int indexOf(Role[] values, Role target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        throw new AssertionError(target + " not found in " + java.util.Arrays.toString(values));
    }
}
