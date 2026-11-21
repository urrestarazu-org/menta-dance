package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
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
@DisplayName("RejectPaymentUseCase")
class RejectPaymentUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String PAYMENT_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";

    @Mock
    private AdminBillingApiClient adminBillingApiClient;

    private RejectPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RejectPaymentUseCaseImpl(adminBillingApiClient);
    }

    private void assertInvalidRequest(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
        verifyNoInteractions(adminBillingApiClient);
    }

    @Test
    @DisplayName("rejects with the normalized id and the trimmed reason")
    void rejects() {
        useCase.execute(TOKEN, " " + PAYMENT_ID.toUpperCase(), "  Monto incorrecto \n");

        verify(adminBillingApiClient).rejectPayment(TOKEN, PAYMENT_ID, "Monto incorrecto");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("a missing or blank reason is an INVALID_REQUEST, without calling the API")
    void blankReason(String reason) {
        assertInvalidRequest(() -> useCase.execute(TOKEN, PAYMENT_ID, reason));
    }

    @Test
    @DisplayName("a reason of 500 characters goes through, one of 501 is an INVALID_REQUEST")
    void reasonLimit() {
        useCase.execute(TOKEN, PAYMENT_ID, "x".repeat(500));
        verify(adminBillingApiClient).rejectPayment(TOKEN, PAYMENT_ID, "x".repeat(500));

        assertThatThrownBy(() -> useCase.execute(TOKEN, PAYMENT_ID, "x".repeat(501)))
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
    }

    @Test
    @DisplayName("the limit counts the trimmed reason")
    void limitCountsTrimmed() {
        useCase.execute(TOKEN, PAYMENT_ID, "  " + "x".repeat(500) + "  ");

        verify(adminBillingApiClient).rejectPayment(TOKEN, PAYMENT_ID, "x".repeat(500));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not-a-uuid"})
    @DisplayName("an id that is not a UUID is a PAYMENT_NOT_FOUND, without calling the API")
    void invalidId(String id) {
        assertThatThrownBy(() -> useCase.execute(TOKEN, id, "Motivo"))
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.PAYMENT_NOT_FOUND));

        verifyNoInteractions(adminBillingApiClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("a missing access token is unavailable, without calling the API")
    void missingToken(String token) {
        assertThatThrownBy(() -> useCase.execute(token, PAYMENT_ID, "Motivo"))
                .isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(adminBillingApiClient);
    }
}
