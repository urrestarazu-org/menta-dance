package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VirtualModuleTest {

    @Test
    void rejects_a_negative_order() {
        assertThatThrownBy(() -> new VirtualModule(ModuleId.generate(), CourseId.generate(), "t", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order");
    }

    @Test
    void create_generates_a_fresh_id() {
        CourseId courseId = CourseId.generate();

        VirtualModule module = VirtualModule.create(courseId, "Módulo 1", 0);

        assertThat(module.getId()).isNotNull();
        assertThat(module.getCourseId()).isEqualTo(courseId);
        assertThat(module.getTitle()).isEqualTo("Módulo 1");
        assertThat(module.getOrder()).isZero();
    }

    @Test
    void with_title_and_with_order_return_new_instances() {
        VirtualModule module = VirtualModule.create(CourseId.generate(), "Módulo 1", 0);

        assertThat(module.withTitle("Nuevo").getTitle()).isEqualTo("Nuevo");
        assertThat(module.withOrder(3).getOrder()).isEqualTo(3);
    }
}
