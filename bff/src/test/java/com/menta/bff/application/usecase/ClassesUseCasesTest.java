package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ClassCheckout;
import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.dto.ClassPricing;
import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.dto.ClassQuote;
import com.menta.bff.application.dto.PhysicalClassDetail;
import com.menta.bff.application.dto.PhysicalClassSummary;
import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the four in-person class use cases and their Spanish labels.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("In-person class use cases")
class ClassesUseCasesTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TOKEN = "token";

    @Mock
    private PhysicalCatalogApiClient catalog;

    @Mock
    private PhysicalPurchaseApiClient billing;

    private static ClassQuote quote(boolean available) {
        return new ClassQuote(
                "q-1", "course-p", ClassPurchaseType.MONTHLY, null, new BigDecimal("12000.00"), "ARS", available,
                Instant.parse("2026-10-04T16:15:00Z"));
    }

    @Nested
    @DisplayName("GetClassesViewUseCaseImpl")
    class ListClasses {

        @Test
        @DisplayName("labels level and weekly schedule in Spanish, keeping unknown codes as-is")
        void labelsInSpanish() {
            when(catalog.listClasses()).thenReturn(List.of(
                    new PhysicalClassSummary("c-1", "Salsa", "BEGINNER", "María", "TUESDAY", "19:00", 20),
                    new PhysicalClassSummary("c-2", "Tango", "MASTER", "Juan", "HOLIDAY", "20:30", 12)));

            List<ClassCard> cards = new GetClassesViewUseCaseImpl(catalog).execute();

            assertThat(cards).containsExactly(
                    new ClassCard("c-1", "Salsa", "Inicial", "María", "Martes 19:00", 20),
                    new ClassCard("c-2", "Tango", "MASTER", "Juan", "HOLIDAY 20:30", 12));
        }
    }

    @Nested
    @DisplayName("GetClassDetailViewUseCaseImpl")
    class ClassDetail {

        private final PhysicalClassDetail course = new PhysicalClassDetail(
                "course-p", "Salsa", "INTERMEDIATE", "María", "SATURDAY", "19:00", 20,
                List.of(
                        new PhysicalClassDetail.Session("s-1", Instant.parse("2026-10-06T22:00:00Z"), 20, 17),
                        new PhysicalClassDetail.Session("s-2", Instant.parse("2026-10-13T22:00:00Z"), 20, 0)));

        @Test
        @DisplayName("labels sessions in the academy zone and keeps the published price")
        void labelsSessionsInAcademyZone() {
            ClassPricing pricing = new ClassPricing(new BigDecimal("12000"), "ARS", new BigDecimal("20"));
            when(catalog.getClassDetail("course-p")).thenReturn(course);
            when(billing.findPricing("course-p")).thenReturn(Optional.of(pricing));

            ClassDetailView view = new GetClassDetailViewUseCaseImpl(catalog, billing, BUENOS_AIRES)
                    .execute("course-p");

            assertThat(view.level()).isEqualTo("Intermedio");
            assertThat(view.schedule()).isEqualTo("Sábado 19:00");
            assertThat(view.pricing()).isEqualTo(pricing);
            assertThat(view.sessions()).containsExactly(
                    new ClassDetailView.SessionRow("s-1", "Martes 6/10 · 19:00", 17),
                    new ClassDetailView.SessionRow("s-2", "Martes 13/10 · 19:00", 0));
            assertThat(view.sessions().get(1).soldOut()).isTrue();
            assertThat(view.purchasable()).isTrue();
        }

        @Test
        @DisplayName("without a published price the page is not purchasable")
        void withoutPricing_isNotPurchasable() {
            when(catalog.getClassDetail("course-p")).thenReturn(course);
            when(billing.findPricing("course-p")).thenReturn(Optional.empty());

            ClassDetailView view = new GetClassDetailViewUseCaseImpl(catalog, billing, BUENOS_AIRES)
                    .execute("course-p");

            assertThat(view.pricing()).isNull();
            assertThat(view.purchasable()).isFalse();
        }

        @Test
        @DisplayName("with every session sold out the page is not purchasable")
        void allSoldOut_isNotPurchasable() {
            PhysicalClassDetail full = new PhysicalClassDetail(
                    "course-p", "Salsa", "BEGINNER", "María", "TUESDAY", "19:00", 20,
                    List.of(new PhysicalClassDetail.Session("s-2", Instant.parse("2026-10-13T22:00:00Z"), 20, 0)));
            when(catalog.getClassDetail("course-p")).thenReturn(full);
            when(billing.findPricing("course-p"))
                    .thenReturn(Optional.of(new ClassPricing(BigDecimal.TEN, "ARS", BigDecimal.ONE)));

            assertThat(new GetClassDetailViewUseCaseImpl(catalog, billing, BUENOS_AIRES).execute("course-p")
                    .purchasable()).isFalse();
        }

        @Test
        @DisplayName("an unknown course never reaches billing")
        void unknownCourse_skipsBilling() {
            when(catalog.getClassDetail("nope")).thenThrow(new PhysicalCatalogApiClient.NotFoundException("no"));

            assertThatThrownBy(() -> new GetClassDetailViewUseCaseImpl(catalog, billing, BUENOS_AIRES)
                    .execute("nope"))
                    .isInstanceOf(PhysicalCatalogApiClient.NotFoundException.class);
            verify(billing, never()).findPricing(anyString());
        }
    }

    @Nested
    @DisplayName("QuoteClassUseCaseImpl")
    class Quote {

        private QuoteClassUseCaseImpl useCase() {
            return new QuoteClassUseCaseImpl(billing, BUENOS_AIRES, () -> "key-1");
        }

        @Test
        @DisplayName("an available quote is payable, with a fresh idempotency key and a local expiry")
        void available_isPayable() {
            ClassQuote quote = quote(true);
            when(billing.createQuote("course-p", ClassPurchaseType.MONTHLY, null, TOKEN)).thenReturn(quote);

            ClassQuoteView view = useCase().execute("course-p", ClassPurchaseType.MONTHLY, null, TOKEN);

            assertThat(view).isEqualTo(new ClassQuoteView.Payable("course-p", quote, "4/10 13:15", "key-1"));
        }

        @Test
        @DisplayName("a monthly quote never forwards a session id")
        void monthly_dropsSessionId() {
            when(billing.createQuote(eq("course-p"), eq(ClassPurchaseType.MONTHLY), isNull(), eq(TOKEN)))
                    .thenReturn(quote(true));

            useCase().execute("course-p", ClassPurchaseType.MONTHLY, "s-1", TOKEN);

            verify(billing).createQuote("course-p", ClassPurchaseType.MONTHLY, null, TOKEN);
        }

        @Test
        @DisplayName("an individual quote forwards the session id")
        void individual_forwardsSessionId() {
            when(billing.createQuote("course-p", ClassPurchaseType.INDIVIDUAL, "s-1", TOKEN))
                    .thenReturn(quote(true));

            assertThat(useCase().execute("course-p", ClassPurchaseType.INDIVIDUAL, "s-1", TOKEN))
                    .isInstanceOf(ClassQuoteView.Payable.class);
        }

        @Test
        @DisplayName("an unavailable quote is sold out")
        void unavailable_isSoldOut() {
            when(billing.createQuote(any(), any(), any(), any())).thenReturn(quote(false));

            assertThat(useCase().execute("course-p", ClassPurchaseType.MONTHLY, null, TOKEN))
                    .isEqualTo(new ClassQuoteView.SoldOut("course-p"));
        }

        @Test
        @DisplayName("a refused quote is rejected")
        void refused_isRejected() {
            when(billing.createQuote(any(), any(), any(), any()))
                    .thenThrow(new PhysicalPurchaseApiClient.QuoteRejectedException("422"));

            assertThat(useCase().execute("course-p", ClassPurchaseType.MONTHLY, null, TOKEN))
                    .isEqualTo(new ClassQuoteView.Rejected("course-p"));
        }
    }

    @Nested
    @DisplayName("PurchaseClassUseCaseImpl")
    class Purchase {

        private PurchaseClassUseCaseImpl useCase() {
            return new PurchaseClassUseCaseImpl(billing);
        }

        private final ClassCheckout.BankTransfer instructions = new ClassCheckout.BankTransfer(
                "cbu", "alias", "holder", "cuit", BigDecimal.TEN, "ARS", "SUB-1");

        @Test
        @DisplayName("Mercado Pago redirects to the provider")
        void mercadoPago_redirects() {
            when(billing.checkout("q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                    .thenReturn(new ClassCheckout("pay-1", "PENDING", "https://mp/checkout", null));

            assertThat(useCase().execute("course-p", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                    .isEqualTo(new ClassPurchaseView.RedirectToProvider("https://mp/checkout"));
        }

        @Test
        @DisplayName("bank transfer shows the instructions")
        void bankTransfer_showsInstructions() {
            when(billing.checkout("q-1", ClassPaymentMethod.BANK_TRANSFER, "key-1", TOKEN))
                    .thenReturn(new ClassCheckout("pay-1", "AWAITING", null, instructions));

            assertThat(useCase().execute("course-p", "q-1", ClassPaymentMethod.BANK_TRANSFER, "key-1", TOKEN))
                    .isEqualTo(new ClassPurchaseView.BankTransfer("course-p", instructions));
        }

        @Test
        @DisplayName("a capacity conflict is sold out")
        void conflict_isSoldOut() {
            when(billing.checkout(any(), any(), any(), any()))
                    .thenThrow(new PhysicalPurchaseApiClient.CapacityUnavailableException("409"));

            assertThat(useCase().execute("course-p", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                    .isEqualTo(new ClassPurchaseView.SoldOut("course-p"));
        }

        @Test
        @DisplayName("an expired or rejected quote asks to quote again")
        void expiredOrRejected_isQuoteExpired() {
            when(billing.checkout(any(), any(), any(), any()))
                    .thenThrow(new PhysicalPurchaseApiClient.QuoteExpiredException("410"))
                    .thenThrow(new PhysicalPurchaseApiClient.QuoteRejectedException("400"));

            assertThat(useCase().execute("course-p", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                    .isEqualTo(new ClassPurchaseView.QuoteExpired("course-p"));
            assertThat(useCase().execute("course-p", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                    .isEqualTo(new ClassPurchaseView.QuoteExpired("course-p"));
        }

        @Test
        @DisplayName("a checkout without a way to pay for the chosen rail is a service failure")
        void checkoutWithoutWayToPay_isUnavailable() {
            when(billing.checkout(any(), any(), any(), any()))
                    .thenReturn(new ClassCheckout("pay-1", "PENDING", null, null));

            assertThatThrownBy(() -> useCase().execute(
                    "course-p", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                    .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
            assertThatThrownBy(() -> useCase().execute(
                    "course-p", "q-1", ClassPaymentMethod.BANK_TRANSFER, "key-1", TOKEN))
                    .isInstanceOf(PhysicalPurchaseApiClient.ServiceUnavailableException.class);
        }
    }
}
