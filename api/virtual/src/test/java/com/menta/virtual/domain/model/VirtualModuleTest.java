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
    void defaults_preview_to_false_and_preserves_it_when_other_fields_change() {
        VirtualModule module = VirtualModule.create(CourseId.generate(), "Módulo 1", 0);

        assertThat(module.isPreview()).isFalse();
        assertThat(module.withTitle("Nuevo").isPreview()).isFalse();
        assertThat(module.withOrder(3).isPreview()).isFalse();
    }

    @Test
    void creates_and_updates_a_preview_module() {
        VirtualModule module = VirtualModule.create(CourseId.generate(), "Módulo 1", true, 0);

        assertThat(module.isPreview()).isTrue();
        assertThat(module.withPreview(false).isPreview()).isFalse();
    }
}
