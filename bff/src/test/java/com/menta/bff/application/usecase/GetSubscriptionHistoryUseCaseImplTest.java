package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.dto.SubscriptionHistoryEntry;
import com.menta.bff.application.dto.SubscriptionHistoryEntry.Status;
import com.menta.bff.application.dto.SubscriptionHistoryEntry.Type;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/** Unit tests for {@link GetSubscriptionHistoryUseCaseImpl}: the subscriptions section of the history page (#363). */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetSubscriptionHistoryUseCase")
class GetSubscriptionHistoryUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private SubscriptionApiClient subscriptionApiClient;

    @Mock
    private BillingApiClient billingApiClient;

    private GetSubscriptionHistoryUseCase useCase;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        useCase = new GetSubscriptionHistoryUseCaseImpl(subscriptionApiClient, billingApiClient, BUENOS_AIRES);
        logger = (Logger) LoggerFactory.getLogger(GetSubscriptionHistoryUseCaseImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static SubscriptionHistoryEntry entry(String planId, Status status, Type type) {
        return new SubscriptionHistoryEntry(planId, status, Instant.parse("2026-11-01T02:00:00Z"),
                Instant.parse("2026-12-01T12:00:00Z"), type);
    }

    private static PlanSummary plan(String id, String name) {
        return new PlanSummary(id, name, "d", BigDecimal.TEN, "ARS", 30, false);
    }

    @Test
    @DisplayName("each subscription shows its plan name and its dates in the display zone, in API order")
    void rowsInApiOrder() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN)).thenReturn(List.of(
                entry("p-2", Status.ACTIVE, Type.PAID), entry("p-1", Status.EXPIRED, Type.PAID)));
        when(billingApiClient.getPlans()).thenReturn(List.of(plan("p-1", "Plan Mensual"), plan("p-2", "Plan Anual")));

        SubscriptionHistoryView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        // 2026-11-01T02:00Z is still 31/10 in Buenos Aires (UTC-3).
        assertThat(view.rows()).containsExactly(
                new SubscriptionHistoryView.Row("Plan Anual", "Activa", BadgeTone.SUCCESS, false,
                        "31/10/2026", "01/12/2026"),
                new SubscriptionHistoryView.Row("Plan Mensual", "Vencida", BadgeTone.NEUTRAL, false,
                        "31/10/2026", "01/12/2026"));
    }

    @ParameterizedTest(name = "{0} reads as \"{1}\"")
    @CsvSource({"PENDING,Pendiente de pago,WARNING", "ACTIVE,Activa,SUCCESS", "EXPIRED,Vencida,NEUTRAL",
        "CANCELLED,Cancelada,DANGER", "UNKNOWN,Estado desconocido,NEUTRAL"})
    void statusLabels(Status status, String label, BadgeTone tone) {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN)).thenReturn(List.of(entry("p-1", status, Type.PAID)));
        when(billingApiClient.getPlans()).thenReturn(List.of());

        assertThat(useCase.execute(TOKEN).rows()).singleElement().satisfies(row -> {
            assertThat(row.statusLabel()).isEqualTo(label);
            assertThat(row.tone()).isEqualTo(tone);
        });
    }

    @Test
    @DisplayName("only a TRIAL subscription is flagged as a trial")
    void trialFlag() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN)).thenReturn(List.of(
                entry("p-1", Status.ACTIVE, Type.TRIAL), entry("p-1", Status.ACTIVE, Type.PAID),
                entry("p-1", Status.ACTIVE, Type.UNKNOWN)));
        when(billingApiClient.getPlans()).thenReturn(List.of());

        assertThat(useCase.execute(TOKEN).rows()).extracting(SubscriptionHistoryView.Row::trial)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("a pending subscription has no dates yet")
    void pendingWithoutDates() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN))
                .thenReturn(List.of(new SubscriptionHistoryEntry("p-1", Status.PENDING, null, null, Type.PAID)));
        when(billingApiClient.getPlans()).thenReturn(List.of());

        assertThat(useCase.execute(TOKEN).rows()).singleElement().satisfies(row -> {
            assertThat(row.startLabel()).isEqualTo("Sin fecha");
            assertThat(row.endLabel()).isEqualTo("Sin fecha");
        });
    }

    @Test
    @DisplayName("a plan missing from the catalog shows the generic plan name, never an invented one")
    void planNotInCatalog() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN))
                .thenReturn(List.of(entry("retired", Status.EXPIRED, Type.PAID)));
        when(billingApiClient.getPlans()).thenReturn(List.of(plan("p-1", "Plan Mensual")));

        assertThat(useCase.execute(TOKEN).rows()).singleElement()
                .satisfies(row -> assertThat(row.planName()).isEqualTo(GetDashboardViewUseCaseImpl.GENERIC_PLAN_NAME));
    }

    @Test
    @DisplayName("a catalog failure degrades the plan names but keeps the history")
    void catalogFailureKeepsTheHistory() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN))
                .thenReturn(List.of(entry("p-1", Status.ACTIVE, Type.PAID)));
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.ServiceUnavailableException("down"));

        SubscriptionHistoryView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.rows()).singleElement()
                .satisfies(row -> assertThat(row.planName()).isEqualTo(GetDashboardViewUseCaseImpl.GENERIC_PLAN_NAME));
    }

    @Test
    @DisplayName("no subscriptions is an empty section, not a failure, and needs no catalog call")
    void noSubscriptions() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN)).thenReturn(List.of());

        SubscriptionHistoryView view = useCase.execute(TOKEN);

        assertThat(view.empty()).isTrue();
        assertThat(view.unavailable()).isFalse();
        verifyNoInteractions(billingApiClient);
    }

    @Test
    @DisplayName("an unavailable Billing API becomes the unavailable notice and logs no token")
    void billingUnavailable() {
        when(subscriptionApiClient.listSubscriptionHistory(TOKEN))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("Billing API unavailable"));

        SubscriptionHistoryView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.rows()).isEmpty();
        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("ServiceUnavailableException").doesNotContain(TOKEN));
        verifyNoInteractions(billingApiClient);
    }

    @ParameterizedTest(name = "a token \"{0}\" fails closed to the unavailable notice without calling the API")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingTokenIsUnavailableWithoutCall(String token) {
        assertThat(useCase.execute(token).unavailable()).isTrue();
        verifyNoMoreInteractions(subscriptionApiClient);
        verifyNoInteractions(billingApiClient);
    }
}
