package com.menta.bff.infrastructure.config;

import com.menta.bff.domain.membership.MembershipPriceSchedule;
import com.menta.bff.domain.membership.PriceRow;
import com.menta.bff.domain.membership.PriceRowState;
import com.menta.bff.domain.membership.PriceTier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Which tiers lack their subscription link on a given day (#685).
 *
 * <p>A tier in force today without a link would show no way to subscribe, so it blocks startup. A tier that
 * is not in force yet only warns: its link can be loaded before its turn comes. An expired tier needs none.
 * </p>
 *
 * @param blocking indexes (0-based, in tier order) of the tiers in force today without a link
 * @param warnings indexes of the future tiers without a link
 */
record MembershipLandingLinkAudit(List<Integer> blocking, List<Integer> warnings) {

    static MembershipLandingLinkAudit of(List<PriceTier> tiers, LocalDate today) {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(tiers, today);
        List<Integer> blocking = new ArrayList<>();
        List<Integer> warnings = new ArrayList<>();
        for (int i = 0; i < tiers.size(); i++) {
            if (hasLink(tiers.get(i))) {
                continue;
            }
            PriceRowState state = rows.get(i).state();
            if (state == PriceRowState.CURRENT) {
                blocking.add(i);
            } else if (state == PriceRowState.LATER) {
                warnings.add(i);
            }
        }
        return new MembershipLandingLinkAudit(List.copyOf(blocking), List.copyOf(warnings));
    }

    private static boolean hasLink(PriceTier tier) {
        return tier.payUrl() != null && !tier.payUrl().isBlank();
    }
}
