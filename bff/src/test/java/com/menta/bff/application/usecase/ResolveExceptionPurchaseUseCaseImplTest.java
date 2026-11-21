package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.menta.bff.application.dto.PurchaseResolution;
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
@DisplayName("ResolveExceptionPurchaseUseCase")
class ResolveExceptionPurchaseUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String ID = "7a1b2c3d-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final PurchaseResolution REFUNDED = PurchaseResolution.REFUNDED;

    @Mock
    private AdminBillingApiClient adminBillingApiClient;

    private ResolveExceptionPurchaseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResolveExceptionPurchaseUseCaseImpl(adminBillingApiClient);
    }

    private void assertRefused(Code code, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PaymentRefusalException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
        verifyNoInteractions(adminBillingApiClient);
    }

    @Test
    @DisplayName("resolves with the normalized id and the trimmed note")
    void resolves() {
        useCase.execute(TOKEN, " " + ID.toUpperCase(), PurchaseResolution.COMPENSATED, "  Se compensó \n");

        verify(adminBillingApiClient).resolvePurchase(TOKEN, ID, PurchaseResolution.COMPENSATED, "Se compensó");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("a missing or blank note is an INVALID_REQUEST, without calling the API")
    void blankNote(String note) {
        assertRefused(Code.INVALID_REQUEST, () -> useCase.execute(TOKEN, ID, REFUNDED, note));
    }

    @Test
    @DisplayName("a note of 500 characters (trimmed) goes through, one of 501 is an INVALID_REQUEST")
    void noteLimit() {
        useCase.execute(TOKEN, ID, REFUNDED, "  " + "x".repeat(500) + "  ");
        verify(adminBillingApiClient).resolvePurchase(TOKEN, ID, REFUNDED, "x".repeat(500));

        assertThatThrownBy(() -> useCase.execute(TOKEN, ID, REFUNDED, "x".repeat(501)))
                .isInstanceOfSatisfying(PaymentRefusalException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
    }

    @Test
    @DisplayName("a missing resolution is an INVALID_REQUEST, without calling the API")
    void missingResolution() {
        assertRefused(Code.INVALID_REQUEST, () -> useCase.execute(TOKEN, ID, null, "Nota"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not-a-uuid"})
    @DisplayName("an id that is not a UUID is a PURCHASE_NOT_FOUND, without calling the API")
    void invalidId(String id) {
        assertRefused(Code.PURCHASE_NOT_FOUND, () -> useCase.execute(TOKEN, id, REFUNDED, "Nota"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("a missing access token is unavailable, without calling the API")
    void missingToken(String token) {
        assertThatThrownBy(() -> useCase.execute(token, ID, REFUNDED, "Nota"))
                .isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(adminBillingApiClient);
    }
}
