package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanCourseJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID planId = UUID.randomUUID();

        PlanCourseJpaEntity entity = new PlanCourseJpaEntity(planId, "course-1");

        assertThat(entity.getId()).isNull();
        assertThat(entity.getPlanId()).isEqualTo(planId);
        assertThat(entity.getCourseId()).isEqualTo("course-1");
    }
}
