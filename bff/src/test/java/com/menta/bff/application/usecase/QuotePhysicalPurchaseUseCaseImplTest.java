package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PaymentProgress;
import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuotePhysicalPurchaseUseCase")
class QuotePhysicalPurchaseUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "course-1";
    private static final String QUOTE_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private PhysicalPurchaseApiClient purchaseApi;
    private CatalogApiClient catalogApi;
    private QuotePhysicalPurchaseUseCase quoteUseCase;

    @BeforeEach
    void setUp() {
        purchaseApi = mock(PhysicalPurchaseApiClient.class);
        catalogApi = mock(CatalogApiClient.class);
        quoteUseCase = new QuotePhysicalPurchaseUseCaseImpl(purchaseApi, catalogApi, ZONE, () -> "key-1");
        when(catalogApi.getPhysicalCourse(COURSE_ID)).thenReturn(new PhysicalCourseDetail(
                COURSE_ID, "Salsa en vivo", "ADVANCED", "María García", "MONDAY", "18:00", 12, List.of(
                        new PhysicalCourseDetail.Session("s-1", Instant.parse("2026-10-12T21:00:00Z"), 12, 5))));
    }

    private static PhysicalQuote quote(PhysicalPurchaseType type, String sessionId, boolean unavailable) {
        return new PhysicalQuote(QUOTE_ID, type, 4, sessionId, new BigDecimal("12500.50"), "ARS", unavailable,
                Instant.parse("2026-10-10T13:00:00Z"));
    }

    @Test
    @DisplayName("a monthly quote is worded in Spanish with the price in es-AR and the expiry in the academy zone")
    void monthlyQuote() {
        when(purchaseApi.createQuote(TOKEN, COURSE_ID, PhysicalPurchaseType.MONTHLY, null))
                .thenReturn(quote(PhysicalPurchaseType.MONTHLY, null, false));

        PurchaseQuoteView view = quoteUseCase.execute(TOKEN, COURSE_ID, PhysicalPurchaseType.MONTHLY, null);

        assertThat(view.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(view.idempotencyKey()).isEqualTo("key-1");
        assertThat(view.courseId()).isEqualTo(COURSE_ID);
        assertThat(view.courseTitle()).isEqualTo("Salsa en vivo");
        assertThat(view.modalityLabel()).isEqualTo("Mensualidad: todas las clases del mes");
        assertThat(view.amountLabel()).contains("12.500,50").startsWith("$");
        assertThat(view.sessionCount()).isEqualTo(4);
        assertThat(view.sessionLabel()).isNull();
        assertThat(view.expiresLabel()).isEqualTo("sábado 10 de octubre a las 10:00");
        assertThat(view.unavailable()).isFalse();
    }

    @Test
    @DisplayName("an individual quote names the chosen session; an unknown session leaves the label out")
    void individualQuote() {
        when(purchaseApi.createQuote(TOKEN, COURSE_ID, PhysicalPurchaseType.INDIVIDUAL, "s-1"))
                .thenReturn(quote(PhysicalPurchaseType.INDIVIDUAL, "s-1", false));
        when(purchaseApi.createQuote(TOKEN, COURSE_ID, PhysicalPurchaseType.INDIVIDUAL, "gone"))
                .thenReturn(quote(PhysicalPurchaseType.INDIVIDUAL, "gone", false));

        PurchaseQuoteView known = quoteUseCase.execute(TOKEN, COURSE_ID, PhysicalPurchaseType.INDIVIDUAL, "s-1");
        PurchaseQuoteView unknown = quoteUseCase.execute(TOKEN, COURSE_ID, PhysicalPurchaseType.INDIVIDUAL, "gone");

        assertThat(known.modalityLabel()).isEqualTo("Clase individual");
        assertThat(known.sessionLabel()).isEqualTo("lunes 12 de octubre a las 18:00");
        assertThat(unknown.sessionLabel()).isNull();
    }

    @Test
    @DisplayName("an unavailable projection is carried to the view")
    void unavailableQuote() {
        when(purchaseApi.createQuote(TOKEN, COURSE_ID, PhysicalPurchaseType.MONTHLY, null))
                .thenReturn(quote(PhysicalPurchaseType.MONTHLY, null, true));

        assertThat(quoteUseCase.execute(TOKEN, COURSE_ID, PhysicalPurchaseType.MONTHLY, null).unavailable())
                .isTrue();
    }

    @Test
    @DisplayName("a catalog 404 stops before any quote is created, and a quote failure propagates")
    void failuresPropagate() {
        when(catalogApi.getPhysicalCourse("nope")).thenThrow(new CatalogApiClient.NotFoundException("no"));
        assertThatThrownBy(() -> quoteUseCase.execute(TOKEN, "nope", PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(CatalogApiClient.NotFoundException.class);
        verifyNoInteractions(purchaseApi);

        when(purchaseApi.createQuote(TOKEN, COURSE_ID, PhysicalPurchaseType.MONTHLY, null))
                .thenThrow(new PhysicalPurchaseApiClient.NoScheduledSessionsException("none"));
        assertThatThrownBy(() -> quoteUseCase.execute(TOKEN, COURSE_ID, PhysicalPurchaseType.MONTHLY, null))
                .isInstanceOf(PhysicalPurchaseApiClient.NoScheduledSessionsException.class);
    }

    @Test
    @DisplayName("paying delegates to the Billing port with the caller's token")
    void pay() {
        PhysicalCheckout checkout = new PhysicalCheckout("pay-1", "https://pay.example.com/c/1");
        when(purchaseApi.createCheckout(TOKEN, QUOTE_ID, "key-1")).thenReturn(checkout);

        assertThat(new PayPhysicalPurchaseUseCaseImpl(purchaseApi).execute(TOKEN, QUOTE_ID, "key-1"))
                .isEqualTo(checkout);
    }

    @Test
    @DisplayName("reading the payment progress delegates to the Billing port with the caller's token")
    void paymentProgress() {
        when(purchaseApi.getPaymentProgress(TOKEN, "pay-1")).thenReturn(PaymentProgress.COMPLETED);

        assertThat(new GetPaymentProgressUseCaseImpl(purchaseApi).execute(TOKEN, "pay-1"))
                .isEqualTo(PaymentProgress.COMPLETED);
    }

    @Test
    @DisplayName("only non-pending progress is final, and unknown upstream names read as pending")
    void progressMapping() {
        assertThat(PaymentProgress.PENDING.isFinal()).isFalse();
        assertThat(PaymentProgress.COMPLETED.isFinal()).isTrue();
        assertThat(PaymentProgress.fromUpstream(null)).isEqualTo(PaymentProgress.PENDING);
        assertThat(PaymentProgress.fromUpstream("SOMETHING_NEW")).isEqualTo(PaymentProgress.PENDING);
    }
}
