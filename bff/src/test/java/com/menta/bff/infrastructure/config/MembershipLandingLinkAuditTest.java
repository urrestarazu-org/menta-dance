package com.menta.bff.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.domain.membership.PriceTier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MembershipLandingLinkAudit")
class MembershipLandingLinkAuditTest {

    private static final LocalDate NOV_7 = LocalDate.of(2026, 11, 7);
    private static final LocalDate NOV_16 = LocalDate.of(2026, 11, 16);
    private static final LocalDate DEC_14 = LocalDate.of(2026, 12, 14);

    private static PriceTier tier(LocalDate deadline, String payUrl) {
        return new PriceTier("tier " + deadline, 1_000, 12, deadline, payUrl, false);
    }

    @Test
    @DisplayName("a missing link of a tier in force today blocks; a missing link of a future tier only warns")
    void currentBlocksAndFutureWarns() {
        List<PriceTier> tiers = List.of(
                tier(NOV_7, "https://pay.test/1"), tier(NOV_7, ""), tier(NOV_16, ""), tier(DEC_14, " "));

        MembershipLandingLinkAudit audit = MembershipLandingLinkAudit.of(tiers, LocalDate.of(2026, 11, 1));

        assertThat(audit.blocking()).containsExactly(1);
        assertThat(audit.warnings()).containsExactly(2, 3);
    }

    @Test
    @DisplayName("tiers with a link neither block nor warn")
    void completeLinksAreClean() {
        List<PriceTier> tiers = List.of(tier(NOV_7, "https://pay.test/1"), tier(NOV_16, "https://pay.test/2"));

        MembershipLandingLinkAudit audit = MembershipLandingLinkAudit.of(tiers, LocalDate.of(2026, 11, 1));

        assertThat(audit.blocking()).isEmpty();
        assertThat(audit.warnings()).isEmpty();
    }

    @Test
    @DisplayName("an expired tier without a link is ignored")
    void expiredTiersAreIgnored() {
        List<PriceTier> tiers = List.of(tier(NOV_7, ""), tier(NOV_16, "https://pay.test/2"), tier(DEC_14, ""));

        MembershipLandingLinkAudit audit = MembershipLandingLinkAudit.of(tiers, NOV_7.plusDays(1));

        assertThat(audit.blocking()).isEmpty();
        assertThat(audit.warnings()).containsExactly(2);
    }

    @Test
    @DisplayName("once every deadline has passed there is nothing to block or warn about")
    void salesClosedIsClean() {
        MembershipLandingLinkAudit audit = MembershipLandingLinkAudit.of(
                List.of(tier(NOV_7, ""), tier(NOV_16, "")), DEC_14);

        assertThat(audit.blocking()).isEmpty();
        assertThat(audit.warnings()).isEmpty();
    }
}
