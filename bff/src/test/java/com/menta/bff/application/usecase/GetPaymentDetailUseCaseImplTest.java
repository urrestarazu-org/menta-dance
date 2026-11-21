package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PaymentDetail;
import com.menta.bff.application.dto.PaymentModality;
import com.menta.bff.application.dto.PaymentStatusType;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPaymentDetailUseCase")
class GetPaymentDetailUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String PAYMENT_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final PaymentDetail DETAIL = new PaymentDetail(PAYMENT_ID, "user-1", PaymentModality.PHYSICAL,
            "course-1", new BigDecimal("25000"), "ARS", PaymentStatusType.AWAITING_MANUAL_VERIFICATION,
            Instant.parse("2026-11-20T15:30:00Z"), true);

    @Mock
    private AdminBillingApiClient adminBillingApiClient;

    private GetPaymentDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPaymentDetailUseCaseImpl(adminBillingApiClient);
    }

    @Test
    @DisplayName("normalizes the id (strip, lower case) before asking the API")
    void normalizesTheId() {
        when(adminBillingApiClient.getPaymentDetail(TOKEN, PAYMENT_ID)).thenReturn(DETAIL);

        assertThat(useCase.execute(TOKEN, "  " + PAYMENT_ID.toUpperCase() + " ")).isSameAs(DETAIL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not-a-uuid", "../admin/users", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f/approve"})
    @DisplayName("an id that is not a UUID is a PAYMENT_NOT_FOUND, without calling the API")
    void invalidId(String id) {
        assertThatThrownBy(() -> useCase.execute(TOKEN, id))
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.PAYMENT_NOT_FOUND));

        verifyNoInteractions(adminBillingApiClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("a missing access token is unavailable, without calling the API")
    void missingToken(String token) {
        assertThatThrownBy(() -> useCase.execute(token, PAYMENT_ID)).isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(adminBillingApiClient);
    }

    @Test
    @DisplayName("a refusal of the API reaches the caller untouched")
    void propagatesRefusals() {
        PaymentRefusalException refusal = new PaymentRefusalException(Code.PAYMENT_NOT_FOUND);
        when(adminBillingApiClient.getPaymentDetail(TOKEN, PAYMENT_ID)).thenThrow(refusal);

        assertThatThrownBy(() -> useCase.execute(TOKEN, PAYMENT_ID)).isSameAs(refusal);
    }
}
