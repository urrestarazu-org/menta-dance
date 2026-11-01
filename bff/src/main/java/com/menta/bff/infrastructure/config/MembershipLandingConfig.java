package com.menta.bff.infrastructure.config;

import com.menta.bff.application.usecase.GetMembershipLandingViewUseCase;
import com.menta.bff.application.usecase.GetMembershipLandingViewUseCaseImpl;
import com.menta.bff.domain.membership.PriceTier;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the temporary membership landing (#685). The use case bean exists only while
 * {@code menta.landing.membership.enabled} is true: its absence is how the home page knows to serve the
 * regular catalog.
 *
 * <p>Creating the bean also checks the subscription links against today's date: a tier in force without its
 * link stops the startup, a future tier without it only logs a warning (its row reads "Link de pago
 * próximamente" when its turn comes). See {@link MembershipLandingLinkAudit}.
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "menta.landing.membership", name = "enabled", havingValue = "true")
public class MembershipLandingConfig {

    private static final Logger log = LoggerFactory.getLogger(MembershipLandingConfig.class);

    /** Deadlines are Argentine calendar days; Argentina does not observe daylight saving time. */
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public GetMembershipLandingViewUseCase getMembershipLandingViewUseCase(
            MembershipLandingProperties properties, ObjectProvider<Clock> clockProvider) {
        Clock clock = clockProvider.getIfAvailable(() -> Clock.system(ARGENTINA));
        List<PriceTier> tiers = properties.toPriceTiers();
        MembershipLandingLinkAudit audit = MembershipLandingLinkAudit.of(tiers, LocalDate.now(clock));
        if (!audit.blocking().isEmpty()) {
            throw new IllegalStateException(
                    "menta.landing.membership.enabled is true but a tier in force today has no pay link: "
                            + describe(audit.blocking()));
        }
        if (!audit.warnings().isEmpty()) {
            log.warn("Membership landing: tiers not in force yet have no pay link, set {} before their turn; "
                    + "until then their row reads \"Link de pago próximamente\"", describe(audit.warnings()));
        }
        return new GetMembershipLandingViewUseCaseImpl(tiers, clock);
    }

    private static String describe(List<Integer> indexes) {
        return indexes.stream()
                .map(i -> "menta.landing.membership.tiers[%d] (MENTA_LANDING_MEMBERSHIP_PAY_URL_%02d)"
                        .formatted(i, i + 1))
                .collect(Collectors.joining(", "));
    }
}
