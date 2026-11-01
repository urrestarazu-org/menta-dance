package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.bff.domain.membership.PriceRowState;
import com.menta.bff.domain.membership.PriceTier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMembershipLandingViewUseCaseImpl")
class GetMembershipLandingViewUseCaseImplTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final List<PriceTier> TIERS = List.of(
            new PriceTier("7 de noviembre", 26_000, 12, LocalDate.of(2026, 11, 7), "https://pay.test/1", false),
            new PriceTier("16 de noviembre", 28_000, 12, LocalDate.of(2026, 11, 16), "https://pay.test/2", false));

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ARGENTINA);
    }

    @Test
    @DisplayName("computes the rows with today's date in Argentina, not in UTC")
    void usesTheArgentinaDate() {
        // 2026-11-08T01:00Z is still Nov 7 at 22:00 in Argentina: the Nov 7 tier is current.
        var view = new GetMembershipLandingViewUseCaseImpl(TIERS, clockAt("2026-11-08T01:00:00Z")).execute();

        assertThat(view.priceRows()).extracting(row -> row.state())
                .containsExactly(PriceRowState.CURRENT, PriceRowState.LATER);
        assertThat(view.salesClosed()).isFalse();
    }

    @Test
    @DisplayName("moves to the next tier once Argentina's clock passes midnight")
    void nextTierAfterMidnightInArgentina() {
        var view = new GetMembershipLandingViewUseCaseImpl(TIERS, clockAt("2026-11-08T03:00:00Z")).execute();

        assertThat(view.priceRows()).extracting(row -> row.state())
                .containsExactly(PriceRowState.EXPIRED, PriceRowState.CURRENT);
    }

    @Test
    @DisplayName("reports sales closed after the last deadline")
    void salesClosedAfterTheLastDeadline() {
        var view = new GetMembershipLandingViewUseCaseImpl(TIERS, clockAt("2026-11-17T12:00:00Z")).execute();

        assertThat(view.salesClosed()).isTrue();
    }

    @Test
    @DisplayName("exposes the server instant so the browser countdown can correct its clock")
    void exposesTheServerInstant() {
        var view = new GetMembershipLandingViewUseCaseImpl(TIERS, clockAt("2026-11-01T15:30:00Z")).execute();

        assertThat(view.serverNow()).isEqualTo("2026-11-01T15:30:00Z");
    }

    @Test
    @DisplayName("rejects missing collaborators")
    void rejectsNulls() {
        assertThatThrownBy(() -> new GetMembershipLandingViewUseCaseImpl(null, clockAt("2026-11-01T00:00:00Z")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GetMembershipLandingViewUseCaseImpl(TIERS, null))
                .isInstanceOf(NullPointerException.class);
    }
}
