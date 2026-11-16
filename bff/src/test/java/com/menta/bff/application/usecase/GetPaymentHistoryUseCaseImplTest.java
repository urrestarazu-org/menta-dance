package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.bff.application.dto.OwnPayment;
import com.menta.bff.application.dto.OwnPayment.ItemType;
import com.menta.bff.application.dto.OwnPayment.Method;
import com.menta.bff.application.dto.OwnPayment.Status;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PaymentApiClient;
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

/** Unit tests for {@link GetPaymentHistoryUseCaseImpl}: the payments section of the history page (#363). */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetPaymentHistoryUseCase")
class GetPaymentHistoryUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final int LIMIT = GetPaymentHistoryUseCaseImpl.HISTORY_LIMIT;
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant CREATED_AT = Instant.parse("2026-11-02T01:30:00Z");

    @Mock
    private PaymentApiClient paymentApiClient;

    private GetPaymentHistoryUseCase useCase;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        useCase = new GetPaymentHistoryUseCaseImpl(paymentApiClient, BUENOS_AIRES);
        logger = (Logger) LoggerFactory.getLogger(GetPaymentHistoryUseCaseImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private static OwnPayment payment(Status status, Method method, ItemType type, String name) {
        return new OwnPayment(status, new BigDecimal("4999.50"), "ARS", method, CREATED_AT, type, name);
    }

    @Test
    @DisplayName("the history asks for the default twenty, not the API maximum")
    void historyLimitIsTwenty() {
        assertThat(LIMIT).isEqualTo(20);
    }

    @Test
    @DisplayName("each payment shows date in the display zone, concept, amount, method and status, in API order")
    void rowsInApiOrder() {
        when(paymentApiClient.listOwnPayments(TOKEN, LIMIT)).thenReturn(List.of(
                payment(Status.COMPLETED, Method.MERCADO_PAGO, ItemType.SUBSCRIPTION, "Plan Mensual"),
                payment(Status.AWAITING_MANUAL_VERIFICATION, Method.BANK_TRANSFER, ItemType.PHYSICAL_COURSE, null)));

        PaymentHistoryView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        assertThat(view.rows()).hasSize(2);
        // 2026-11-02T01:30Z is still 01/11 in Buenos Aires (UTC-3).
        PaymentHistoryView.Row first = view.rows().get(0);
        assertThat(first.dateLabel()).isEqualTo("01/11/2026");
        assertThat(first.concept()).isEqualTo("Plan Mensual");
        assertThat(first.amountLabel()).startsWith("$").contains("4.999,50");
        assertThat(first.methodLabel()).isEqualTo("Mercado Pago");
        assertThat(first.statusLabel()).isEqualTo("Pagado");
        assertThat(first.tone()).isEqualTo(BadgeTone.SUCCESS);
        PaymentHistoryView.Row second = view.rows().get(1);
        assertThat(second.concept()).isEqualTo("Curso presencial");
        assertThat(second.methodLabel()).isEqualTo("Transferencia bancaria");
        assertThat(second.statusLabel()).isEqualTo("En verificación");
    }

    @ParameterizedTest(name = "{0} reads as \"{1}\"")
    @CsvSource({"AWAITING_PROVIDER,Pendiente de pago,WARNING", "AWAITING_MANUAL_VERIFICATION,En verificación,WARNING",
        "RECONCILIATION_REQUIRED,En revisión,WARNING", "COMPLETED,Pagado,SUCCESS", "REJECTED,Rechazado,DANGER",
        "CANCELLED,Cancelado,NEUTRAL", "EXPIRED,Vencido,NEUTRAL", "UNKNOWN,Estado desconocido,NEUTRAL"})
    void statusLabels(Status status, String label, BadgeTone tone) {
        when(paymentApiClient.listOwnPayments(TOKEN, LIMIT))
                .thenReturn(List.of(payment(status, Method.MERCADO_PAGO, ItemType.SUBSCRIPTION, "Plan")));

        assertThat(useCase.execute(TOKEN).rows()).singleElement().satisfies(row -> {
            assertThat(row.statusLabel()).isEqualTo(label);
            assertThat(row.tone()).isEqualTo(tone);
        });
    }

    @Test
    @DisplayName("a subscription payment without a plan name, or an item of an unknown kind, gets a generic concept")
    void genericConcepts() {
        when(paymentApiClient.listOwnPayments(TOKEN, LIMIT)).thenReturn(List.of(
                payment(Status.COMPLETED, Method.MERCADO_PAGO, ItemType.SUBSCRIPTION, null),
                payment(Status.COMPLETED, Method.MERCADO_PAGO, ItemType.SUBSCRIPTION, "  "),
                payment(Status.COMPLETED, Method.UNKNOWN, ItemType.UNKNOWN, "Algo")));

        assertThat(useCase.execute(TOKEN).rows()).extracting(PaymentHistoryView.Row::concept)
                .containsExactly("Suscripción", "Suscripción", "Compra");
        assertThat(useCase.execute(TOKEN).rows().get(2).methodLabel()).isEqualTo("Otro medio de pago");
    }

    @Test
    @DisplayName("a currency code that is not ISO 4217 keeps the amount and the code as they came")
    void unreadableCurrency() {
        when(paymentApiClient.listOwnPayments(TOKEN, LIMIT)).thenReturn(List.of(new OwnPayment(
                Status.COMPLETED, new BigDecimal("10.00"), "ZZZ-1", Method.MERCADO_PAGO, CREATED_AT,
                ItemType.SUBSCRIPTION, "Plan")));

        assertThat(useCase.execute(TOKEN).rows()).singleElement()
                .satisfies(row -> assertThat(row.amountLabel()).isEqualTo("10.00 ZZZ-1"));
    }

    @Test
    @DisplayName("no payments is an empty section, not a failure")
    void noPayments() {
        when(paymentApiClient.listOwnPayments(TOKEN, LIMIT)).thenReturn(List.of());

        PaymentHistoryView view = useCase.execute(TOKEN);

        assertThat(view.empty()).isTrue();
        assertThat(view.unavailable()).isFalse();
    }

    @Test
    @DisplayName("an unavailable Billing API becomes the unavailable notice and logs no token")
    void billingUnavailable() {
        when(paymentApiClient.listOwnPayments(TOKEN, LIMIT))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("Billing API unavailable"));

        PaymentHistoryView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.rows()).isEmpty();
        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("ServiceUnavailableException").doesNotContain(TOKEN));
    }

    @ParameterizedTest(name = "a token \"{0}\" fails closed to the unavailable notice without calling the API")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingTokenIsUnavailableWithoutCall(String token) {
        assertThat(useCase.execute(token).unavailable()).isTrue();
        verifyNoInteractions(paymentApiClient);
    }
}
