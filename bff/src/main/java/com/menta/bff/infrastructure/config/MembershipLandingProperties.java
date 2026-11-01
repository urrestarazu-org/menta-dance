package com.menta.bff.infrastructure.config;

import com.menta.bff.domain.membership.PriceTier;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Temporary membership landing served at {@code /} while it is enabled (#685).
 *
 * <p>Binds to application.yml properties prefixed with {@code menta.landing.membership}. It is disabled by
 * default; the tiers, their dates and the subscription links known so far live in application.yml. Each tier's
 * Mercado Pago link can be overridden, or loaded when it does not exist yet, with its own environment variable
 * ({@code MENTA_LANDING_MEMBERSHIP_PAY_URL_01} to {@code _13}).
 * </p>
 *
 * <p>When the landing is enabled the application refuses to start if a tier is incomplete or if a pay link
 * is present but is not an https URL. A missing link is not checked here: whether it blocks startup depends
 * on the date (only the tiers in force today need one), see {@link MembershipLandingConfig}. When the landing
 * is disabled nothing is validated.
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "menta.landing.membership")
public class MembershipLandingProperties implements InitializingBean {

    private static final String PROPERTY_PREFIX = "menta.landing.membership.tiers";

    private boolean enabled = false;
    private List<Tier> tiers = new ArrayList<>();

    /** A price tier as bound from configuration. */
    @Data
    public static class Tier {
        private String label = "";
        private int price;
        private int installments;
        private LocalDate deadline;
        private String payUrl = "";
        private boolean exclusive;
    }

    /** The configured tiers as domain objects, in the order they are shown. */
    public List<PriceTier> toPriceTiers() {
        return tiers.stream()
                .map(tier -> new PriceTier(
                        tier.getLabel(), tier.getPrice(), tier.getInstallments(), tier.getDeadline(),
                        tier.getPayUrl(), tier.isExclusive()))
                .toList();
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (tiers.isEmpty()) {
            problems.add("it needs at least one tier");
        }
        for (int i = 0; i < tiers.size(); i++) {
            collectProblems(i, tiers.get(i), problems);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "menta.landing.membership.enabled is true but the configuration is not usable: "
                            + String.join("; ", problems));
        }
    }

    private static void collectProblems(int index, Tier tier, List<String> problems) {
        String where = PROPERTY_PREFIX + "[" + index + "]";
        if (tier.getLabel() == null || tier.getLabel().isBlank()) {
            problems.add(where + " has no label");
        }
        if (tier.getPrice() <= 0) {
            problems.add(where + " has no positive price");
        }
        if (tier.getInstallments() <= 0) {
            problems.add(where + " has no positive number of installments");
        }
        if (tier.getDeadline() == null) {
            problems.add(where + " has no deadline");
        }
        boolean hasLink = tier.getPayUrl() != null && !tier.getPayUrl().isBlank();
        if (hasLink && !isHttpsUrl(tier.getPayUrl())) {
            problems.add(where + " needs an https pay link (check MENTA_LANDING_MEMBERSHIP_PAY_URL_"
                    + "%02d)".formatted(index + 1));
        }
    }

    private static boolean isHttpsUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
