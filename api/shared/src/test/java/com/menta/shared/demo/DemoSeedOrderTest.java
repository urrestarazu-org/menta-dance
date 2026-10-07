package com.menta.shared.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DemoSeedOrderTest {

    @Test
    void auth_runs_before_content_which_runs_before_billing() {
        assertThat(DemoSeedOrder.AUTH).isLessThan(DemoSeedOrder.CONTENT);
        assertThat(DemoSeedOrder.CONTENT).isLessThan(DemoSeedOrder.BILLING);
    }

    @Test
    void demo_ids_are_distinct() {
        Set<UUID> ids = Set.of(
            DemoIds.ADMIN_USER_ID,
            DemoIds.INSTRUCTOR_USER_ID,
            DemoIds.STUDENT_USER_ID,
            DemoIds.RECEPTIONIST_USER_ID,
            DemoIds.VIRTUAL_SENSUAL_COURSE_ID,
            DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID,
            DemoIds.PHYSICAL_SENSUAL_COURSE_ID,
            DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID
        );

        assertThat(ids).hasSize(8);
    }
}
