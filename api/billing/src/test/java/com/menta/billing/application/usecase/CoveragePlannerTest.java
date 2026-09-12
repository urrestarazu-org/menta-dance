package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.usecase.CoveragePlanner.EligibleSession;
import com.menta.billing.application.usecase.CoveragePlanner.Plan;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN for {@link CoveragePlanner} (design A4, A5, A6) — a pure function
 * over Billing's own {@link ScheduledSessionSnapshot} out-port shape, so no
 * mock is needed anywhere in this suite; it carries this module's 100%
 * domain+application coverage floor on its own.
 */
class CoveragePlannerTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final String SESSION_1 = "11111111-1111-1111-1111-111111111111";
    private static final String SESSION_2 = "22222222-2222-2222-2222-222222222222";
    private static final String SESSION_3 = "33333333-3333-3333-3333-333333333333";
    private static final String SESSION_4 = "44444444-4444-4444-4444-444444444444";

    @Test
    void monthly_dense_calendar_picks_the_next_n_sessions_in_order() {
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_3, CONFIRMED_AT.plus(Duration.ofDays(14)), 5),
            new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 5),
            new ScheduledSessionSnapshot(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(7)), 5),
            new ScheduledSessionSnapshot(SESSION_4, CONFIRMED_AT.plus(Duration.ofDays(21)), 5)
        );

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 3, false);

        assertThat(plan).isInstanceOf(Plan.Complete.class);
        assertThat(((Plan.Complete) plan).sessions()).containsExactly(
            new EligibleSession(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1))),
            new EligibleSession(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(7))),
            new EligibleSession(SESSION_3, CONFIRMED_AT.plus(Duration.ofDays(14)))
        );
    }

    @Test
    void monthly_skips_a_calendar_gap_and_extends_forward_without_truncating() {
        // Only 2 sessions in the two weeks after confirmation, then a gap, then more.
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 5),
            new ScheduledSessionSnapshot(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(8)), 5),
            new ScheduledSessionSnapshot(SESSION_3, CONFIRMED_AT.plus(Duration.ofDays(45)), 5),
            new ScheduledSessionSnapshot(SESSION_4, CONFIRMED_AT.plus(Duration.ofDays(52)), 5)
        );

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 4, false);

        assertThat(plan).isInstanceOf(Plan.Complete.class);
        assertThat(((Plan.Complete) plan).sessions()).containsExactly(
            new EligibleSession(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1))),
            new EligibleSession(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(8))),
            new EligibleSession(SESSION_3, CONFIRMED_AT.plus(Duration.ofDays(45))),
            new EligibleSession(SESSION_4, CONFIRMED_AT.plus(Duration.ofDays(52)))
        );
    }

    @Test
    void monthly_shortfall_inside_the_horizon_is_insufficient_never_a_partial_list() {
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 5),
            new ScheduledSessionSnapshot(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(8)), 5),
            new ScheduledSessionSnapshot(SESSION_3, CONFIRMED_AT.plus(Duration.ofDays(119)), 5)
        );

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 4, false);

        assertThat(plan).isEqualTo(new Plan.Insufficient());
    }

    @Test
    void monthly_horizon_lower_bound_is_inclusive_of_the_reference_instant() {
        List<ScheduledSessionSnapshot> calendar =
            List.of(new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT, 5));

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 1, false);

        assertThat(plan).isEqualTo(new Plan.Complete(List.of(new EligibleSession(SESSION_1, CONFIRMED_AT))));
    }

    @Test
    void monthly_horizon_upper_bound_is_exclusive_at_exactly_120_days() {
        Instant exactlyAtHorizon = CONFIRMED_AT.plus(CoveragePlanner.COVERAGE_LOOKAHEAD);
        List<ScheduledSessionSnapshot> calendar =
            List.of(new ScheduledSessionSnapshot(SESSION_1, exactlyAtHorizon, 5));

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 1, false);

        assertThat(plan).isEqualTo(new Plan.Insufficient());
    }

    @Test
    void monthly_ignores_sessions_before_the_reference_instant() {
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.minus(Duration.ofDays(1)), 5),
            new ScheduledSessionSnapshot(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(1)), 5)
        );

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 1, false);

        assertThat(plan)
            .isEqualTo(new Plan.Complete(List.of(new EligibleSession(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(1))))));
    }

    @Test
    void monthly_require_available_true_filters_out_zero_availability_sessions() {
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 0),
            new ScheduledSessionSnapshot(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(2)), 5)
        );

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 1, true);

        assertThat(plan)
            .isEqualTo(new Plan.Complete(List.of(new EligibleSession(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(2))))));
    }

    @Test
    void monthly_require_available_false_does_not_filter_zero_availability_sessions() {
        List<ScheduledSessionSnapshot> calendar =
            List.of(new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 0));

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 1, false);

        assertThat(plan)
            .isEqualTo(new Plan.Complete(List.of(new EligibleSession(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1))))));
    }

    @Test
    void monthly_breaks_a_same_instant_tie_by_session_id_ascending() {
        Instant sameInstant = CONFIRMED_AT.plus(Duration.ofDays(1));
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_3, sameInstant, 5),
            new ScheduledSessionSnapshot(SESSION_1, sameInstant, 5)
        );

        Plan plan = CoveragePlanner.planMonthly(calendar, CONFIRMED_AT, 2, false);

        assertThat(plan).isEqualTo(new Plan.Complete(List.of(
            new EligibleSession(SESSION_1, sameInstant),
            new EligibleSession(SESSION_3, sameInstant)
        )));
    }

    @Test
    void monthly_rejects_a_non_positive_scheduled_session_count() {
        assertThatThrownBy(() -> CoveragePlanner.planMonthly(List.of(), CONFIRMED_AT, 0, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monthly_rejects_null_scheduled_sessions() {
        assertThatThrownBy(() -> CoveragePlanner.planMonthly(null, CONFIRMED_AT, 1, false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void monthly_rejects_null_reference_instant() {
        assertThatThrownBy(() -> CoveragePlanner.planMonthly(List.of(), null, 1, false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void individual_returns_exactly_the_selected_session() {
        List<ScheduledSessionSnapshot> calendar = List.of(
            new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 5),
            new ScheduledSessionSnapshot(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(2)), 5)
        );

        Plan plan = CoveragePlanner.planIndividual(calendar, SESSION_2, false);

        assertThat(plan)
            .isEqualTo(new Plan.Complete(List.of(new EligibleSession(SESSION_2, CONFIRMED_AT.plus(Duration.ofDays(2))))));
    }

    @Test
    void individual_missing_selected_session_is_insufficient() {
        List<ScheduledSessionSnapshot> calendar =
            List.of(new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 5));

        Plan plan = CoveragePlanner.planIndividual(calendar, SESSION_2, false);

        assertThat(plan).isEqualTo(new Plan.Insufficient());
    }

    @Test
    void individual_require_available_true_treats_a_full_session_as_insufficient() {
        List<ScheduledSessionSnapshot> calendar =
            List.of(new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 0));

        Plan plan = CoveragePlanner.planIndividual(calendar, SESSION_1, true);

        assertThat(plan).isEqualTo(new Plan.Insufficient());
    }

    @Test
    void individual_require_available_false_accepts_a_full_session() {
        List<ScheduledSessionSnapshot> calendar =
            List.of(new ScheduledSessionSnapshot(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1)), 0));

        Plan plan = CoveragePlanner.planIndividual(calendar, SESSION_1, false);

        assertThat(plan)
            .isEqualTo(new Plan.Complete(List.of(new EligibleSession(SESSION_1, CONFIRMED_AT.plus(Duration.ofDays(1))))));
    }

    @Test
    void individual_rejects_null_scheduled_sessions() {
        assertThatThrownBy(() -> CoveragePlanner.planIndividual(null, SESSION_1, false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void individual_rejects_null_selected_session_id() {
        assertThatThrownBy(() -> CoveragePlanner.planIndividual(List.of(), null, false))
            .isInstanceOf(NullPointerException.class);
    }
}
