package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.SubscriptionCheckout;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SubscribeToPlanUseCase")
class SubscribeToPlanUseCaseImplTest {

    private final SubscriptionApiClient api = mock(SubscriptionApiClient.class);
    private final SubscribeToPlanUseCase useCase = new SubscribeToPlanUseCaseImpl(api);

    @Test
    @DisplayName("the rendered key travels to the port untouched and the checkout is returned")
    void usesTheRenderedKey() {
        SubscriptionCheckout checkout = new SubscriptionCheckout("pay-1", "https://pay.example.com/c/1");
        when(api.createSubscriptionCheckout("token", "plan-1", "key-1")).thenReturn(checkout);

        assertThat(useCase.execute("token", "plan-1", "key-1")).isSameAs(checkout);
    }

    @Test
    @DisplayName("a double submit reuses the same key, so the API can return the original checkout")
    void doubleSubmitSameKey() {
        when(api.createSubscriptionCheckout("token", "plan-1", "key-1"))
                .thenReturn(new SubscriptionCheckout("pay-1", "https://pay.example.com/c/1"));

        SubscriptionCheckout first = useCase.execute("token", "plan-1", "key-1");
        SubscriptionCheckout second = useCase.execute("token", "plan-1", "key-1");

        assertThat(second).isEqualTo(first);
        verify(api, times(2)).createSubscriptionCheckout("token", "plan-1", "key-1");
    }
}
