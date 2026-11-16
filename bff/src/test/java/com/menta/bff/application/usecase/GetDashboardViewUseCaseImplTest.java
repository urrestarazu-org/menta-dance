package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.DashboardView;
import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.dto.SubscriptionHistoryEntry;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link GetDashboardViewUseCaseImpl}: the student's subscription card on the
 * dashboard, read with the caller's token, with the plan name resolved from the public catalog.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetDashboardViewUseCase")
class GetDashboardViewUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    // 2026-10-31T01:30Z is still 30/10 in Buenos Aires: the label must use the display zone.
    private static final Instant END = Instant.parse("2026-10-31T01:30:00Z");
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    private static final List<PlanSummary> PLANS = List.of(
            new PlanSummary("plan-1", "Plan Mensual", "Un mes", BigDecimal.valueOf(1000), "ARS", 30, false),
            new PlanSummary("plan-2", "Plan Anual", "Un año", BigDecimal.valueOf(10000), "ARS", 365, true));

    @Mock
    private BillingApiClient billingApiClient;

    @Mock
    private SubscriptionApiClient subscriptionApiClient;

    private GetDashboardViewUseCase useCase;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        useCase = new GetDashboardViewUseCaseImpl(
                billingApiClient, subscriptionApiClient, ZoneId.of("America/Argentina/Buenos_Aires"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        logger = (Logger) LoggerFactory.getLogger(GetDashboardViewUseCaseImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("a student without a subscription gets the none card and the catalog is not read")
    void none() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.None());

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.None());
        verifyNoInteractions(billingApiClient);
    }

    @Test
    @DisplayName("an ACTIVE subscription shows the plan name, the end date in the display zone and the days left")
    void active() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Active("plan-2", END, 17L, false));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN))
                .isEqualTo(new DashboardView.Active("Plan Anual", "30/10/2026", "Vence en 17 días", false));
    }

    @Test
    @DisplayName("an ACTIVE subscription about to expire keeps the flag so the card can warn")
    void activeExpiringSoon() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Active("plan-1", END, 3L, true));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN))
                .isEqualTo(new DashboardView.Active("Plan Mensual", "30/10/2026", "Vence en 3 días", true));
    }

    @ParameterizedTest(name = "{0} days remaining reads \"{1}\"")
    @CsvSource({"0, Vence hoy", "1, Vence en 1 día", "2, Vence en 2 días"})
    void remainingLabel(long days, String label) {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Active("plan-1", END, days, true));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(((DashboardView.Active) useCase.execute(TOKEN)).remainingLabel()).isEqualTo(label);
    }

    @Test
    @DisplayName("an ACTIVE subscription without the days left shows no remaining label")
    void activeWithoutDaysRemaining() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Active("plan-1", END, null, false));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN))
                .isEqualTo(new DashboardView.Active("Plan Mensual", "30/10/2026", null, false));
    }

    @Test
    @DisplayName("a PENDING subscription shows the plan being paid for")
    void pending() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Pending("0f0e0d0c-1111-2222-3333-444444444444", "plan-1"));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Pending("Plan Mensual"));
    }

    @Test
    @DisplayName("an EXPIRED subscription shows the plan and the date it ended")
    void expired() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Expired("plan-1", END));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Expired("Plan Mensual", "30/10/2026"));
    }

    @Test
    @DisplayName("a plan missing from the catalog degrades to the generic label and keeps the state")
    void planMissingFromCatalog() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Expired("plan-retired", END));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Expired("Tu plan", "30/10/2026"));
    }

    @Test
    @DisplayName("a subscription without a plan id degrades to the generic label without reading the catalog")
    void planIdMissing() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Pending("0f0e0d0c-1111-2222-3333-444444444444", null));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Pending("Tu plan"));
        verifyNoInteractions(billingApiClient);
    }

    @Test
    @DisplayName("an unavailable catalog degrades to the generic label, logs a warning and never the token")
    void catalogUnavailable() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Active("plan-1", END, 17L, false));
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.ServiceUnavailableException("down"));

        assertThat(useCase.execute(TOKEN))
                .isEqualTo(new DashboardView.Active("Tu plan", "30/10/2026", "Vence en 17 días", false));
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN))
                .noneMatch(event -> event.getFormattedMessage().contains(TOKEN));
    }

    @Test
    @DisplayName("a catalog with no plans (404) also degrades to the generic label")
    void catalogNotFound() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Expired("plan-1", END));
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.NotFoundException("no plans"));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Expired("Tu plan", "30/10/2026"));
    }

    @Test
    @DisplayName("an unverifiable subscription becomes the unavailable card and the catalog is not read")
    void subscriptionUnavailable() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("unavailable"));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Unavailable());
        verifyNoInteractions(billingApiClient);
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN))
                .noneMatch(event -> event.getFormattedMessage().contains(TOKEN));
    }

    @Test
    @DisplayName("each card names its kind, so the template can choose what to render")
    void kinds() {
        assertThat(new DashboardView.None().kind()).isEqualTo(DashboardView.Kind.NONE);
        assertThat(new DashboardView.Active("p", "d", null, false).kind()).isEqualTo(DashboardView.Kind.ACTIVE);
        assertThat(new DashboardView.Pending("p").kind()).isEqualTo(DashboardView.Kind.PENDING);
        assertThat(new DashboardView.Expired("p", "d").kind()).isEqualTo(DashboardView.Kind.EXPIRED);
        assertThat(new DashboardView.Unavailable().kind()).isEqualTo(DashboardView.Kind.UNAVAILABLE);
        assertThat(new DashboardView.Cancelled("p", "d").kind()).isEqualTo(DashboardView.Kind.CANCELLED);
    }

    private static SubscriptionHistoryEntry entry(
            SubscriptionHistoryEntry.Status status, Instant endDate) {
        return new SubscriptionHistoryEntry(
                "plan-1", status, Instant.parse("2026-10-01T03:00:00Z"), endDate, SubscriptionHistoryEntry.Type.PAID);
    }

    private void noCurrentSubscription(SubscriptionHistoryEntry... history) {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.None());
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN)).thenReturn(List.of(history));
    }

    @Test
    @DisplayName("no current subscription but a CANCELLED one that still grants access shows the cancelled card")
    void cancelledWithAccess() {
        // 2026-12-01T01:30Z is still 30/11 in Buenos Aires: the label uses the display zone.
        noCurrentSubscription(entry(SubscriptionHistoryEntry.Status.CANCELLED, Instant.parse("2026-12-01T01:30:00Z")));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Cancelled("Plan Mensual", "30/11/2026"));
    }

    @Test
    @DisplayName("with several CANCELLED ones still granting access, the one that lasts longest is shown")
    void latestCancelledWins() {
        noCurrentSubscription(
                entry(SubscriptionHistoryEntry.Status.CANCELLED, Instant.parse("2026-11-20T12:00:00Z")),
                entry(SubscriptionHistoryEntry.Status.CANCELLED, Instant.parse("2026-12-20T12:00:00Z")),
                entry(SubscriptionHistoryEntry.Status.CANCELLED, Instant.parse("2026-11-25T12:00:00Z")));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Cancelled("Plan Mensual", "20/12/2026"));
    }

    @Test
    @DisplayName("the cancelled card degrades to the generic plan name when the catalog fails")
    void cancelledCatalogUnavailable() {
        noCurrentSubscription(entry(SubscriptionHistoryEntry.Status.CANCELLED, Instant.parse("2026-12-20T12:00:00Z")));
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.ServiceUnavailableException("down"));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.Cancelled("Tu plan", "20/12/2026"));
    }

    @Test
    @DisplayName("a CANCELLED one whose access already ended, or ends right now, is not \"access until\": none card")
    void cancelledWithoutAccess() {
        noCurrentSubscription(
                entry(SubscriptionHistoryEntry.Status.CANCELLED, Instant.parse("2026-11-01T12:00:00Z")),
                entry(SubscriptionHistoryEntry.Status.CANCELLED, NOW),
                entry(SubscriptionHistoryEntry.Status.CANCELLED, null));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.None());
        verifyNoInteractions(billingApiClient);
    }

    @Test
    @DisplayName("only a CANCELLED entry counts: other states with a future end date keep the none card")
    void otherStatesAreIgnored() {
        noCurrentSubscription(
                entry(SubscriptionHistoryEntry.Status.ACTIVE, Instant.parse("2026-12-20T12:00:00Z")),
                entry(SubscriptionHistoryEntry.Status.UNKNOWN, Instant.parse("2026-12-20T12:00:00Z")));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.None());
    }

    @Test
    @DisplayName("a history failure leaves the none card, logs a warning and never the token")
    void historyUnavailable() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.None());
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("down"));

        assertThat(useCase.execute(TOKEN)).isEqualTo(new DashboardView.None());
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN))
                .noneMatch(event -> event.getFormattedMessage().contains(TOKEN));
    }

    @Test
    @DisplayName("a current subscription never reads the history")
    void currentSubscriptionSkipsHistory() {
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Expired("plan-1", END));
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        useCase.execute(TOKEN);

        verify(subscriptionApiClient, never()).listSubscriptionHistory(TOKEN);
    }
}
