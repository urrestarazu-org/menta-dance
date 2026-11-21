package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ExceptionPurchase;
import com.menta.bff.application.dto.ExceptionPurchasePage;
import com.menta.bff.application.dto.PaymentModality;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListExceptionPurchasesUseCase")
class ListExceptionPurchasesUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String USER_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";

    @Mock
    private AdminBillingApiClient adminBillingApiClient;

    private ListExceptionPurchasesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListExceptionPurchasesUseCaseImpl(
                adminBillingApiClient, ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    private static ExceptionPurchase purchase(PaymentModality modality, List<String> sessions) {
        // 02:30 UTC is still the previous evening in Buenos Aires.
        return new ExceptionPurchase("pur-1", USER_ID, modality, new BigDecimal("1234567.5"), "ARS",
                Instant.parse("2026-11-21T02:30:00Z"), sessions);
    }

    @ParameterizedTest(name = "page {0}, size {1} asks the API for page {2}, size {3}")
    @CsvSource(nullValues = "null", textBlock =
            """
            null, null, 0, 20
            3,    10,   3, 10
            -1,   0,    0, 20
            1,    50,   1, 50
            1,    1000, 1, 50
            """)
    @DisplayName("normalizes the paging: page from 0, size 20 by default, never above the API's 50")
    void normalizesPaging(Integer page, Integer size, int askedPage, int askedSize) {
        when(adminBillingApiClient.listPurchasesInException(TOKEN, askedPage, askedSize))
                .thenReturn(new ExceptionPurchasePage(List.of(), askedPage, askedSize, 0, 0));

        assertThat(useCase.execute(TOKEN, page, size).rows()).isEmpty();

        verify(adminBillingApiClient).listPurchasesInException(TOKEN, askedPage, askedSize);
    }

    @Test
    @DisplayName("words each row: short student id, es-AR amount, date in the academy's zone, sessions, fixed state")
    void formatsRows() {
        when(adminBillingApiClient.listPurchasesInException(TOKEN, 2, 10)).thenReturn(new ExceptionPurchasePage(
                List.of(purchase(PaymentModality.PHYSICAL, List.of("s-1", "s-2", "s-3"))), 2, 10, 41, 5));

        ExceptionPurchasesView view = useCase.execute(TOKEN, 2, 10);

        assertThat(view.rows()).singleElement().satisfies(row -> {
            assertThat(row.purchaseId()).isEqualTo("pur-1");
            assertThat(row.studentLabel()).isEqualTo("5b6f7c8e");
            assertThat(row.amountLabel()).isEqualTo("1.234.567,50 ARS");
            assertThat(row.createdLabel()).isEqualTo("20 de noviembre de 2026, 23:30");
            assertThat(row.modalityLabel()).isEqualTo("Presencial");
            assertThat(row.sessionCount()).isEqualTo(3);
            assertThat(row.statusLabel()).isEqualTo("En excepción");
        });
        assertThat(view).extracting(ExceptionPurchasesView::page, ExceptionPurchasesView::size,
                ExceptionPurchasesView::totalPages, ExceptionPurchasesView::totalElements,
                ExceptionPurchasesView::unavailable).containsExactly(2, 10, 5, 41L, false);
        assertThat(view.hasPrevious()).isTrue();
        assertThat(view.hasNext()).isTrue();
        assertThat(view.paginated()).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"VIRTUAL, Virtual", "UNKNOWN, Sin dato"})
    @DisplayName("words the other modalities in Spanish, and an unknown one as 'Sin dato'")
    void wordsTheModality(PaymentModality modality, String label) {
        when(adminBillingApiClient.listPurchasesInException(TOKEN, 0, 20)).thenReturn(
                new ExceptionPurchasePage(List.of(purchase(modality, List.of())), 0, 20, 1, 1));

        ExceptionPurchaseView row = useCase.execute(TOKEN, null, null).rows().get(0);

        assertThat(row.modalityLabel()).isEqualTo(label);
        assertThat(row.sessionCount()).isZero();
    }

    @Test
    @DisplayName("the unavailable view has no rows and is neither empty nor paginated; an empty page is empty")
    void degradedViews() {
        ExceptionPurchasesView unavailable = ExceptionPurchasesView.unavailableView();

        assertThat(unavailable.unavailable()).isTrue();
        assertThat(unavailable.rows()).isEmpty();
        assertThat(unavailable.empty()).isFalse();
        assertThat(unavailable.paginated()).isFalse();
        assertThat(new ExceptionPurchasesView(List.of(), 0, 20, 0, 0, false).empty()).isTrue();
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
        when(adminBillingApiClient.listPurchasesInException(TOKEN, 0, 20)).thenThrow(failure);

        assertThatThrownBy(() -> useCase.execute(TOKEN, null, null)).isSameAs(failure);
    }
}
