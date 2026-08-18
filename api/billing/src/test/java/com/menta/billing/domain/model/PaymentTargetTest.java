package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentTargetTest {

    @Test
    void physical_exposes_the_session_id() {
        assertThat(new PaymentTarget.Physical("session-1").sessionId()).isEqualTo("session-1");
    }

    @Test
    void physical_rejects_null_or_blank_session_id() {
        assertThatThrownBy(() -> new PaymentTarget.Physical(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentTarget.Physical(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void virtual_exposes_the_course_id() {
        assertThat(new PaymentTarget.Virtual("course-1").courseId()).isEqualTo("course-1");
    }

    @Test
    void virtual_rejects_null_or_blank_course_id() {
        assertThatThrownBy(() -> new PaymentTarget.Virtual(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentTarget.Virtual(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
