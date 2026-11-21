package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PendingPaymentPage;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
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
@DisplayName("ListPendingPaymentsUseCase")
class ListPendingPaymentsUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final PendingPaymentPage PAGE = new PendingPaymentPage(List.of(), 0, 20, 0, 0);

    @Mock
    private AdminBillingApiClient adminBillingApiClient;

    private ListPendingPaymentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListPendingPaymentsUseCaseImpl(adminBillingApiClient);
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

        assertThat(useCase.execute(TOKEN, page, size)).isSameAs(PAGE);

        verify(adminBillingApiClient).listPendingPayments(TOKEN, askedPage, askedSize);
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
