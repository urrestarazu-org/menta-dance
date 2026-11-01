package com.menta.bff.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.usecase.GetMembershipLandingViewUseCase;
import com.menta.bff.domain.membership.PriceTier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The temporary membership landing (#685) is off by default and, when on, refuses to start without a working
 * https pay link for every tier. The tiers are the ones of the membership document, bound from the real
 * {@code application.yml}; the pay links come from the environment variables the runbook documents.
 */
@DisplayName("Membership landing configuration")
@ExtendWith(OutputCaptureExtension.class)
class MembershipLandingConfigurationTest {

    /** Installments and totals of the membership document (13 tiers). */
    private static final int[][] DOCUMENT_TIERS = {
        // price, installments, total
        {25_000, 12, 300_000}, {26_000, 12, 312_000}, {28_000, 12, 336_000}, {30_000, 12, 360_000},
        {32_000, 12, 384_000}, {34_000, 11, 374_000}, {36_000, 10, 360_000}, {38_000, 9, 342_000},
        {40_000, 8, 320_000}, {40_000, 7, 280_000}, {40_000, 5, 200_000}, {40_000, 3, 120_000},
        {40_000, 2, 80_000}
    };

    private static final String[] DEADLINES = {
        "2026-11-07", "2026-11-07", "2026-11-16", "2026-12-14", "2027-01-11", "2027-02-15", "2027-03-15",
        "2027-04-12", "2027-05-17", "2027-06-14", "2027-08-16", "2027-09-13", "2027-10-11"
    };

    @Configuration
    @EnableConfigurationProperties(MembershipLandingProperties.class)
    @Import(MembershipLandingConfig.class)
    static class TestConfig {
    }

    /** "Today" of the tests: the first day of the real campaign, when the first two tiers are in force. */
    private static final Instant NOV_1_NOON = Instant.parse("2026-11-01T15:00:00Z");

    private ApplicationContextRunner runner() {
        return runnerOn(NOV_1_NOON);
    }

    private ApplicationContextRunner runnerOn(Instant now) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TestConfig.class)
                .withBean("membershipTestClock", Clock.class,
                        () -> Clock.fixed(now, ZoneId.of("America/Argentina/Buenos_Aires")));
    }

    private static String[] payUrls(String scheme) {
        List<String> values = new ArrayList<>();
        IntStream.rangeClosed(1, 13).forEach(n ->
                values.add("MENTA_LANDING_MEMBERSHIP_PAY_URL_%02d=%s://pay.example.test/plan-%d"
                        .formatted(n, scheme, n)));
        values.add("MENTA_LANDING_MEMBERSHIP_ENABLED=true");
        return values.toArray(String[]::new);
    }

    @Test
    @DisplayName("is disabled by default and starts without any pay link")
    void disabledByDefault() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MembershipLandingProperties.class).isEnabled()).isFalse();
            assertThat(context).doesNotHaveBean(GetMembershipLandingViewUseCase.class);
        });
    }

    @Test
    @DisplayName("application.yml carries the 13 tiers of the membership document with their totals")
    void ymlCarriesTheDocumentTiers() {
        runner().run(context -> {
            List<PriceTier> tiers = context.getBean(MembershipLandingProperties.class).toPriceTiers();

            assertThat(tiers).hasSize(13);
            for (int i = 0; i < DOCUMENT_TIERS.length; i++) {
                PriceTier tier = tiers.get(i);
                assertThat(tier.price()).as("price of tier %d", i + 1).isEqualTo(DOCUMENT_TIERS[i][0]);
                assertThat(tier.installments()).as("installments of tier %d", i + 1).isEqualTo(DOCUMENT_TIERS[i][1]);
                assertThat((long) tier.price() * tier.installments())
                        .as("total of tier %d", i + 1).isEqualTo(DOCUMENT_TIERS[i][2]);
                assertThat(tier.deadline()).as("deadline of tier %d", i + 1).isEqualTo(LocalDate.parse(DEADLINES[i]));
            }
        });
    }

    @Test
    @DisplayName("only the first tier is the perfect-attendance benefit and the labels follow the document")
    void exclusiveTierAndLabels() {
        runner().run(context -> {
            List<PriceTier> tiers = context.getBean(MembershipLandingProperties.class).toPriceTiers();

            assertThat(tiers).extracting(PriceTier::exclusive).containsExactly(
                    true, false, false, false, false, false, false, false, false, false, false, false, false);
            assertThat(tiers.get(0).label()).contains("Asistencia perfecta 2026").contains("7 de noviembre");
            assertThat(tiers.get(11).label()).contains("13 de septiembre 2027").contains("diciembre bonificada");
            assertThat(tiers.get(12).label()).contains("11 de octubre 2027").contains("diciembre bonificada");
        });
    }

    @Test
    @DisplayName("application.yml carries the subscription links of the first five tiers and none for the rest")
    void ymlCarriesTheKnownSubscriptionLinks() {
        runner().run(context -> {
            List<PriceTier> tiers = context.getBean(MembershipLandingProperties.class).toPriceTiers();

            assertThat(tiers.subList(0, 5)).extracting(PriceTier::payUrl).containsExactly(
                    "https://mpago.la/2aMPPhW", "https://mpago.la/2hsJjN6", "https://mpago.la/2HyTR5v",
                    "https://mpago.la/1JzoqKD", "https://mpago.la/1uYYMr2");
            assertThat(tiers.subList(5, 13)).extracting(PriceTier::payUrl).allMatch(String::isBlank);
        });
    }

    @Test
    @DisplayName("an environment variable overrides the link carried by application.yml")
    void environmentOverridesTheYmlLink() {
        runner().withPropertyValues("MENTA_LANDING_MEMBERSHIP_PAY_URL_02=https://pay.example.test/two")
                .run(context -> {
                    List<PriceTier> tiers = context.getBean(MembershipLandingProperties.class).toPriceTiers();

                    assertThat(tiers.get(0).payUrl()).isEqualTo("https://mpago.la/2aMPPhW");
                    assertThat(tiers.get(1).payUrl()).isEqualTo("https://pay.example.test/two");
                });
    }

    @Test
    @DisplayName("enabled with every https link: starts and exposes the use case")
    void enabledWithAllLinksStarts() {
        runner().withPropertyValues(payUrls("https")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GetMembershipLandingViewUseCase.class);
        });
    }

    @Test
    @DisplayName("enabled with a blank link on a tier in force today: refuses to start and names tier and variable")
    void enabledWithBlankCurrentLinkFails() {
        String[] values = payUrls("https");
        values[0] = "MENTA_LANDING_MEMBERSHIP_PAY_URL_01=";

        runner().withPropertyValues(values).run(context -> assertFailureMentions(context,
                "tiers[0]", "MENTA_LANDING_MEMBERSHIP_PAY_URL_01"));
    }

    @Test
    @DisplayName("enabled with only the links of application.yml: starts and warns about the tiers without one")
    void enabledWithOnlyTheKnownLinksStartsAndWarns(CapturedOutput output) {
        runner().withPropertyValues("MENTA_LANDING_MEMBERSHIP_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GetMembershipLandingViewUseCase.class);
            assertThat(output.getAll())
                    .contains("MENTA_LANDING_MEMBERSHIP_PAY_URL_06")
                    .contains("MENTA_LANDING_MEMBERSHIP_PAY_URL_13")
                    .doesNotContain("MENTA_LANDING_MEMBERSHIP_PAY_URL_05");
        });
    }

    @Test
    @DisplayName("after the first tiers expire, only the tiers still in force need a link")
    void expiredTiersNeedNoLink() {
        runnerOn(Instant.parse("2026-11-20T15:00:00Z")).withPropertyValues(
                "MENTA_LANDING_MEMBERSHIP_ENABLED=true",
                "MENTA_LANDING_MEMBERSHIP_PAY_URL_04=https://pay.example.test/plan-4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GetMembershipLandingViewUseCase.class);
                });
    }

    @Test
    @DisplayName("a malformed link refuses to start even on a tier that is not in force yet")
    void malformedFutureLinkFails() {
        String[] values = payUrls("https");
        values[8] = "MENTA_LANDING_MEMBERSHIP_PAY_URL_09=http://pay.example.test/plan-9";

        runner().withPropertyValues(values).run(context -> assertFailureMentions(context,
                "tiers[8]", "https"));
    }

    @Test
    @DisplayName("enabled with a non-https link: refuses to start")
    void enabledWithPlainHttpLinkFails() {
        runner().withPropertyValues(payUrls("http")).run(context -> assertFailureMentions(context, "https"));
    }

    @Test
    @DisplayName("enabled with a link that is not a URL: refuses to start")
    void enabledWithGarbageLinkFails() {
        String[] values = payUrls("https");
        values[0] = "MENTA_LANDING_MEMBERSHIP_PAY_URL_01=https://";

        runner().withPropertyValues(values).run(context -> assertFailureMentions(context, "tiers[0]"));
    }

    @Test
    @DisplayName("enabled without any tier: refuses to start")
    void enabledWithoutTiersFails() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("menta.landing.membership.enabled=true")
                .run(context -> assertFailureMentions(context, "at least one tier"));
    }

    @Test
    @DisplayName("enabled with an incomplete tier: refuses to start instead of failing at the first request")
    void enabledWithIncompleteTierFails() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "menta.landing.membership.enabled=true",
                        "menta.landing.membership.tiers[0].pay-url=https://pay.example.test/x")
                .run(context -> assertFailureMentions(context, "tiers[0]"));
    }

    private static void assertFailureMentions(AssertableApplicationContext context, String... fragments) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class);
        for (String fragment : fragments) {
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(fragment);
        }
    }
}
