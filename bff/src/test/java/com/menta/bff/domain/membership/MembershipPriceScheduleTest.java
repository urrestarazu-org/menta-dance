package com.menta.bff.domain.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MembershipPriceSchedule")
class MembershipPriceScheduleTest {

    private static final LocalDate NOV_7 = LocalDate.of(2026, 11, 7);
    private static final LocalDate NOV_16 = LocalDate.of(2026, 11, 16);
    private static final LocalDate DEC_14 = LocalDate.of(2026, 12, 14);

    /** Same shape as the real schedule: the first two tiers share their deadline. */
    private static final List<PriceTier> TIERS = List.of(
            new PriceTier("Asistencia perfecta 2026", 25_000, 12, NOV_7, "https://pay.test/1", true),
            new PriceTier("Sábado 7 de noviembre", 26_000, 12, NOV_7, "https://pay.test/2", false),
            new PriceTier("16 de noviembre", 28_000, 12, NOV_16, "https://pay.test/3", false),
            new PriceTier("14 de diciembre", 30_000, 12, DEC_14, "https://pay.test/4", false));

    private static List<PriceRowState> statesAt(LocalDate today) {
        return MembershipPriceSchedule.rowsAt(TIERS, today).stream().map(PriceRow::state).toList();
    }

    @Test
    @DisplayName("before the first deadline both tiers sharing the nearest deadline are current")
    void sharedNearestDeadlineIsCurrentForBothTiers() {
        assertThat(statesAt(LocalDate.of(2026, 11, 1))).containsExactly(
                PriceRowState.CURRENT, PriceRowState.CURRENT, PriceRowState.LATER, PriceRowState.LATER);
    }

    @Test
    @DisplayName("the deadline day is inclusive: on Nov 7 the Nov 7 tiers are still current")
    void deadlineDayIsInclusive() {
        assertThat(statesAt(NOV_7)).containsExactly(
                PriceRowState.CURRENT, PriceRowState.CURRENT, PriceRowState.LATER, PriceRowState.LATER);
    }

    @Test
    @DisplayName("the day after a deadline expires its tiers and promotes the next one")
    void dayAfterDeadlineExpiresTheTier() {
        assertThat(statesAt(NOV_7.plusDays(1))).containsExactly(
                PriceRowState.EXPIRED, PriceRowState.EXPIRED, PriceRowState.CURRENT, PriceRowState.LATER);
    }

    @Test
    @DisplayName("on the last deadline only the last tier is current")
    void lastDeadlineIsCurrent() {
        assertThat(statesAt(DEC_14)).containsExactly(
                PriceRowState.EXPIRED, PriceRowState.EXPIRED, PriceRowState.EXPIRED, PriceRowState.CURRENT);
    }

    @Test
    @DisplayName("long before the first tier nothing is expired and the nearest deadline is current")
    void farBeforeTheFirstTier() {
        assertThat(statesAt(LocalDate.of(2025, 1, 1))).containsExactly(
                PriceRowState.CURRENT, PriceRowState.CURRENT, PriceRowState.LATER, PriceRowState.LATER);
    }

    @Test
    @DisplayName("after the last deadline every tier is expired and sales are closed")
    void allExpiredMeansSalesClosed() {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(TIERS, DEC_14.plusDays(1));

        assertThat(rows).allMatch(PriceRow::expired);
        assertThat(MembershipPriceSchedule.isSalesClosed(rows)).isTrue();
    }

    @Test
    @DisplayName("sales are open while some tier is current")
    void salesOpenWhileSomeTierIsCurrent() {
        assertThat(MembershipPriceSchedule.isSalesClosed(MembershipPriceSchedule.rowsAt(TIERS, NOV_16))).isFalse();
    }

    @Test
    @DisplayName("rows keep the order of the tiers")
    void rowsKeepTierOrder() {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(TIERS, NOV_16);

        assertThat(rows).extracting(PriceRow::label).containsExactly(
                "Asistencia perfecta 2026", "Sábado 7 de noviembre", "16 de noviembre", "14 de diciembre");
    }

    @ParameterizedTest(name = "{0} x {1} cuotas = {2} / {3}")
    @CsvSource({
        "25000, 12, $ 25.000, $ 300.000",
        "34000, 11, $ 34.000, $ 374.000",
        "40000, 2, $ 40.000, $ 80.000",
        "40000, 3, $ 40.000, $ 120.000"
    })
    @DisplayName("formats the installment and the total (price x installments) in es-AR")
    void formatsInstallmentAndTotal(int price, int installments, String formattedPrice, String formattedTotal) {
        PriceTier tier = new PriceTier("x", price, installments, NOV_7, "https://pay.test/x", false);

        PriceRow row = MembershipPriceSchedule.rowsAt(List.of(tier), NOV_7).get(0);

        assertThat(row.formattedPrice()).isEqualTo(formattedPrice);
        assertThat(row.formattedTotal()).isEqualTo(formattedTotal);
        assertThat(row.installments()).isEqualTo(installments);
    }

    @Test
    @DisplayName("only a current row with a pay link is payable; locked and expired rows are not")
    void onlyCurrentRowsWithLinkArePayable() {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(TIERS, NOV_7.plusDays(1));

        assertThat(rows).extracting(PriceRow::payable).containsExactly(false, false, true, false);
        assertThat(rows).extracting(PriceRow::locked).containsExactly(true, true, false, true);
        assertThat(rows).extracting(PriceRow::expired).containsExactly(true, true, false, false);
    }

    @Test
    @DisplayName("a current row without a link awaits its link; rows with a link or not current do not")
    void currentRowWithoutLinkAwaitsItsLink() {
        PriceTier withoutLink = new PriceTier("x", 1_000, 1, NOV_7, " ", false);
        PriceTier later = new PriceTier("y", 1_000, 1, NOV_16, "", false);

        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(List.of(withoutLink, later), NOV_7);

        assertThat(rows).extracting(PriceRow::awaitingLink).containsExactly(true, false);
        assertThat(MembershipPriceSchedule.rowsAt(TIERS, NOV_7)).extracting(PriceRow::awaitingLink)
                .containsOnly(false);
    }

    @Test
    @DisplayName("a current tier without a pay link is not payable")
    void blankPayLinkIsNotPayable() {
        PriceTier tier = new PriceTier("x", 1_000, 1, NOV_7, " ", false);

        assertThat(MembershipPriceSchedule.rowsAt(List.of(tier), NOV_7).get(0).payable()).isFalse();
    }

    @Test
    @DisplayName("a price expires at 00:00 Argentina time of the day after the deadline")
    void expiresAtIsMidnightArgentinaTheNextDay() {
        PriceRow row = MembershipPriceSchedule.rowsAt(TIERS, NOV_7).get(0);

        assertThat(row.expiresAt()).isEqualTo("2026-11-08T03:00:00Z");
    }

    @Test
    @DisplayName("exposes the deadline and the exclusive flag of the tier")
    void keepsDeadlineAndExclusiveFlag() {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(TIERS, NOV_7);

        assertThat(rows.get(0).exclusive()).isTrue();
        assertThat(rows.get(1).exclusive()).isFalse();
        assertThat(rows.get(2).deadline()).isEqualTo(NOV_16);
        assertThat(rows.get(0).payUrl()).isEqualTo("https://pay.test/1");
    }

    @Test
    @DisplayName("current() is true only for the current state")
    void currentFlag() {
        assertThat(MembershipPriceSchedule.rowsAt(TIERS, NOV_7)).extracting(PriceRow::current)
                .containsExactly(true, true, false, false);
    }

    @Test
    @DisplayName("a tier rejects a non-positive price or number of installments and a missing deadline")
    void tierRejectsInvalidValues() {
        assertThatThrownBy(() -> new PriceTier("x", 0, 12, NOV_7, "u", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceTier("x", 1_000, 0, NOV_7, "u", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceTier("x", 1_000, 1, null, "u", false))
                .isInstanceOf(NullPointerException.class);
    }
}
