package com.menta.bff.application.usecase;

import com.menta.bff.domain.membership.MembershipPriceSchedule;
import com.menta.bff.domain.membership.PriceRow;
import com.menta.bff.domain.membership.PriceTier;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link GetMembershipLandingViewUseCase}.
 *
 * <p>The table is computed on every request from the clock, so it changes by itself when a deadline passes. The
 * clock carries the Argentina time zone, which is the one the deadlines are expressed in.
 * </p>
 */
public class GetMembershipLandingViewUseCaseImpl implements GetMembershipLandingViewUseCase {

    private final List<PriceTier> tiers;
    private final Clock clock;

    public GetMembershipLandingViewUseCaseImpl(List<PriceTier> tiers, Clock clock) {
        this.tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers cannot be null"));
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public MembershipLandingView execute() {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(tiers, LocalDate.now(clock));
        return new MembershipLandingView(
                rows, MembershipPriceSchedule.isSalesClosed(rows), clock.instant().toString());
    }
}
