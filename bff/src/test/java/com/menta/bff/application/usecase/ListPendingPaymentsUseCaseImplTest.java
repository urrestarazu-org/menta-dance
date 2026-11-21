package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PaymentModality;
import com.menta.bff.application.dto.PaymentStatusType;
import com.menta.bff.application.dto.PendingPayment;
import com.menta.bff.application.dto.PendingPaymentPage;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListPendingPaymentsUseCase")
class ListPendingPaymentsUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final PendingPaymentPage PAGE = new PendingPaymentPage(List.of(), 0, 20, 0, 0);

    @Mock
    private AdminBillingApiClient adminBillingApiClient;

    private ListPendingPaymentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListPendingPaymentsUseCaseImpl(
                adminBillingApiClient, ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @ParameterizedTest(name = "page {0}, size {1} asks the API for page {2}, size {3}")
    @CsvSource(nullValues = "null", textBlock =
            """
            null, null, 0, 20
            0,    20,   0, 20
            3,    10,   3, 10
            -1,   10,   0, 10
            2,    0,    2, 20
            2,    -5,   2, 20
            1,    50,   1, 50
            1,    51,   1, 50
            1,    1000, 1, 50
            """)
    @DisplayName("normalizes the paging: page from 0, size 20 by default, never above the API's 50")
    void normalizesPaging(Integer page, Integer size, int askedPage, int askedSize) {
        when(adminBillingApiClient.listPendingPayments(TOKEN, askedPage, askedSize)).thenReturn(PAGE);

        assertThat(useCase.execute(TOKEN, page, size).rows()).isEmpty();

        verify(adminBillingApiClient).listPendingPayments(TOKEN, askedPage, askedSize);
    }

    @Test
    @DisplayName("words each row for the screen: short student id, es-AR amount, date in the academy's zone")
    void formatsRows() {
        // 02:30 UTC is still the previous evening in Buenos Aires.
        PendingPayment payment = new PendingPayment("pay-1", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f",
                PaymentModality.PHYSICAL, "course-1", new BigDecimal("1234567.5"), "ARS",
                PaymentStatusType.AWAITING_MANUAL_VERIFICATION, true, Instant.parse("2026-11-21T02:30:00Z"));
        when(adminBillingApiClient.listPendingPayments(TOKEN, 2, 10))
                .thenReturn(new PendingPaymentPage(List.of(payment), 2, 10, 41, 5));

        PendingPaymentsView view = useCase.execute(TOKEN, 2, 10);

        assertThat(view.rows()).singleElement().satisfies(row -> {
            assertThat(row.paymentId()).isEqualTo("pay-1");
            assertThat(row.studentLabel()).isEqualTo("5b6f7c8e");
            assertThat(row.amountLabel()).isEqualTo("1.234.567,50 ARS");
            assertThat(row.createdLabel()).isEqualTo("20 de noviembre de 2026, 23:30");
            assertThat(row.methodLabel()).isEqualTo("Transferencia");
            assertThat(row.modalityLabel()).isEqualTo("Presencial");
            assertThat(row.statusLabel()).isEqualTo("Pendiente de verificación");
        });
        assertThat(view).extracting(PendingPaymentsView::page, PendingPaymentsView::size,
                PendingPaymentsView::totalPages, PendingPaymentsView::totalElements,
                PendingPaymentsView::unavailable).containsExactly(2, 10, 5, 41L, false);
        assertThat(view.hasPrevious()).isTrue();
        assertThat(view.hasNext()).isTrue();
        assertThat(view.paginated()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PaymentModality.class)
    @DisplayName("words the modality in Spanish, and an unknown one as 'Sin dato'")
    void wordsTheModality(PaymentModality modality) {
        PendingPayment payment = new PendingPayment("pay-1", "u-1", modality, "ref", BigDecimal.TEN, "ARS",
                PaymentStatusType.AWAITING_MANUAL_VERIFICATION, false, Instant.parse("2026-11-20T15:30:00Z"));
        when(adminBillingApiClient.listPendingPayments(TOKEN, 0, 20))
                .thenReturn(new PendingPaymentPage(List.of(payment), 0, 20, 1, 1));

        String label = useCase.execute(TOKEN, null, null).rows().get(0).modalityLabel();

        assertThat(label).isEqualTo(switch (modality) {
            case PHYSICAL -> "Presencial";
            case VIRTUAL -> "Virtual";
            case UNKNOWN -> "Sin dato";
        });
    }

    @Test
    @DisplayName("the unavailable view has no rows and is neither empty nor paginated")
    void unavailableView() {
        PendingPaymentsView view = PendingPaymentsView.unavailableView();

        assertThat(view.unavailable()).isTrue();
        assertThat(view.rows()).isEmpty();
        assertThat(view.empty()).isFalse();
        assertThat(view.paginated()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("a missing access token is unavailable, without calling the API")
    void missingToken(String token) {
        assertThatThrownBy(() -> useCase.execute(token, 0, 20)).isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(adminBillingApiClient);
    }

    @Test
    @DisplayName("a failure of the API reaches the caller untouched")
    void propagatesFailures() {
        ServiceUnavailableException failure = new ServiceUnavailableException("down");
        when(adminBillingApiClient.listPendingPayments(TOKEN, 0, 20)).thenThrow(failure);

        assertThatThrownBy(() -> useCase.execute(TOKEN, null, null)).isSameAs(failure);
    }
}
