package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanTest {

    private static Plan activePlan() {
        return new Plan(
            PlanId.generate(), "Plan Mensual", "Acceso completo por 30 dias",
            Money.of(new BigDecimal("15000.00"), "ARS"), 30, false, PlanStatus.ACTIVE,
            "Terminos", "Politica de cancelacion", List.of(PlanCourse.of("course-1")),
            Set.of(PaymentMethod.MERCADO_PAGO)
        );
    }

    @Test
    void exposes_every_field_passed_at_construction() {
        Plan plan = activePlan();

        assertThat(plan.getName()).isEqualTo("Plan Mensual");
        assertThat(plan.getDescription()).isEqualTo("Acceso completo por 30 dias");
        assertThat(plan.getPrice().getAmount()).isEqualByComparingTo("15000.00");
        assertThat(plan.getDurationDays()).isEqualTo(30);
        assertThat(plan.isFeatured()).isFalse();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.getTermsAndConditions()).isEqualTo("Terminos");
        assertThat(plan.getCancellationPolicy()).isEqualTo("Politica de cancelacion");
        assertThat(plan.getCourses()).containsExactly(PlanCourse.of("course-1"));
        assertThat(plan.getPaymentMethods()).containsExactly(PaymentMethod.MERCADO_PAGO);
        assertThat(plan.courseIds()).containsExactly("course-1");
    }

    @Test
    void isActive_reflects_status() {
        Plan active = activePlan();
        Plan inactive = new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.INACTIVE, "T", "C", List.of(), Set.of(PaymentMethod.MERCADO_PAGO)
        );

        assertThat(active.isActive()).isTrue();
        assertThat(inactive.isActive()).isFalse();
    }

    @Test
    void accepts_only_the_configured_payment_methods() {
        Plan transferOnly = new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", List.of(), Set.of(PaymentMethod.BANK_TRANSFER)
        );

        assertThat(transferOnly.accepts(PaymentMethod.BANK_TRANSFER)).isTrue();
        assertThat(transferOnly.accepts(PaymentMethod.MERCADO_PAGO)).isFalse();
    }

    @Test
    void courses_list_is_immutable_and_defensively_copied() {
        List<PlanCourse> mutable = new java.util.ArrayList<>(List.of(PlanCourse.of("course-1")));
        Plan plan = new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", mutable, Set.of(PaymentMethod.MERCADO_PAGO)
        );
        mutable.add(PlanCourse.of("course-2"));

        assertThat(plan.getCourses()).hasSize(1);
        assertThatThrownBy(() -> plan.getCourses().add(PlanCourse.of("course-3")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void payment_methods_set_is_immutable_and_defensively_copied() {
        Set<PaymentMethod> mutable = new java.util.HashSet<>(Set.of(PaymentMethod.MERCADO_PAGO));
        Plan plan = new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", List.of(), mutable
        );
        mutable.add(PaymentMethod.BANK_TRANSFER);

        assertThat(plan.getPaymentMethods()).hasSize(1);
        assertThatThrownBy(() -> plan.getPaymentMethods().add(PaymentMethod.BANK_TRANSFER))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_a_non_positive_duration() {
        assertThatThrownBy(() -> new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 0,
            false, PlanStatus.ACTIVE, "T", "C", List.of(), Set.of(PaymentMethod.MERCADO_PAGO)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_empty_payment_method_set() {
        assertThatThrownBy(() -> new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", List.of(), Set.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_required_fields() {
        assertThatThrownBy(() -> new Plan(
            null, "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", List.of(), Set.of(PaymentMethod.MERCADO_PAGO)
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", null, Set.of(PaymentMethod.MERCADO_PAGO)
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Plan(
            PlanId.generate(), "Plan", "Desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "T", "C", List.of(), null
        )).isInstanceOf(NullPointerException.class);
    }
}
