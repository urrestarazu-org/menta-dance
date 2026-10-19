package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.dto.PlansOffer.OfferNotice;
import com.menta.bff.application.dto.PlansOffer.SubscribeAction;
import com.menta.bff.application.dto.PlansView;
import com.menta.bff.application.dto.PlansViewer;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GetPlansViewUseCaseImpl}: the catalog is shared, the offer depends on the
 * viewer and, for a student, on the subscription state read with the caller's token.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetPlansViewUseCase")
class GetPlansViewUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    // 2026-10-31T01:30Z is still 30/10 in Buenos Aires: the label must use the display zone.
    private static final Instant END = Instant.parse("2026-10-31T01:30:00Z");

    private static final List<PlanSummary> PLANS = List.of(
            new PlanSummary("plan-1", "Mensual", "Acceso mensual", BigDecimal.valueOf(1000), "ARS", 30, false),
            new PlanSummary("plan-2", "Anual", "Acceso anual", BigDecimal.valueOf(10000), "ARS", 365, true));

    @Mock
    private BillingApiClient billingApiClient;

    @Mock
    private SubscriptionApiClient subscriptionApiClient;

    private GetPlansViewUseCase useCase;

    @BeforeEach
    void setUp() {
        AtomicInteger counter = new AtomicInteger();
        useCase = new GetPlansViewUseCaseImpl(billingApiClient, subscriptionApiClient,
                ZoneId.of("America/Argentina/Buenos_Aires"), () -> "key-" + counter.incrementAndGet());
    }

    @Test
    @DisplayName("an anonymous visitor gets the plans unchanged, a login link, and no subscription call")
    void anonymous() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        PlansView view = useCase.execute(PlansViewer.ANONYMOUS, null);

        assertThat(view.plans()).isSameAs(PLANS);
        assertThat(view.offer().action()).isEqualTo(SubscribeAction.LOGIN);
        assertThat(view.offer().notice()).isEqualTo(OfferNotice.NONE);
        assertThat(view.offer().idempotencyKeyByPlanId()).isEmpty();
        verifyNoInteractions(subscriptionApiClient);
    }

    @Test
    @DisplayName("another authenticated role gets the plans with no control and no subscription call")
    void otherRole() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);

        PlansView view = useCase.execute(PlansViewer.OTHER, TOKEN);

        assertThat(view.plans()).isSameAs(PLANS);
        assertThat(view.offer().action()).isEqualTo(SubscribeAction.NONE);
        assertThat(view.offer().notice()).isEqualTo(OfferNotice.NONE);
        verifyNoInteractions(subscriptionApiClient);
    }

    @Test
    @DisplayName("a student without a subscription can subscribe, with one fresh key per plan")
    void studentWithoutSubscription() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.None());

        PlansView view = useCase.execute(PlansViewer.STUDENT, TOKEN);

        assertThat(view.offer().action()).isEqualTo(SubscribeAction.SUBSCRIBE);
        assertThat(view.offer().notice()).isEqualTo(OfferNotice.NONE);
        assertThat(view.offer().idempotencyKeyByPlanId())
                .containsOnlyKeys("plan-1", "plan-2")
                .doesNotContainValue(null);
        assertThat(view.offer().idempotencyKeyByPlanId().values()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a student with an ACTIVE subscription sees its end date in the display zone and no control")
    void studentActive() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.Active(END));

        PlansView view = useCase.execute(PlansViewer.STUDENT, TOKEN);

        assertThat(view.offer().action()).isEqualTo(SubscribeAction.NONE);
        assertThat(view.offer().notice()).isEqualTo(OfferNotice.ACTIVE);
        assertThat(view.offer().endDateLabel()).isEqualTo("30/10/2026");
        assertThat(view.offer().idempotencyKeyByPlanId()).isEmpty();
    }

    @Test
    @DisplayName("a student with a PENDING subscription sees the full id and no control")
    void studentPending() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenReturn(new CurrentSubscription.Pending("0f0e0d0c-1111-2222-3333-444444444444"));

        PlansView view = useCase.execute(PlansViewer.STUDENT, TOKEN);

        assertThat(view.offer().action()).isEqualTo(SubscribeAction.NONE);
        assertThat(view.offer().notice()).isEqualTo(OfferNotice.PENDING);
        assertThat(view.offer().subscriptionId()).isEqualTo("0f0e0d0c-1111-2222-3333-444444444444");
    }

    @Test
    @DisplayName("a student with an EXPIRED subscription sees the end date and can subscribe again")
    void studentExpired() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.Expired(END));

        PlansView view = useCase.execute(PlansViewer.STUDENT, TOKEN);

        assertThat(view.offer().action()).isEqualTo(SubscribeAction.SUBSCRIBE);
        assertThat(view.offer().notice()).isEqualTo(OfferNotice.EXPIRED);
        assertThat(view.offer().endDateLabel()).isEqualTo("30/10/2026");
        assertThat(view.offer().idempotencyKeyByPlanId()).containsOnlyKeys("plan-1", "plan-2");
    }

    @Test
    @DisplayName("a failure to read the subscription propagates and no offer is built")
    void subscriptionFailurePropagates() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);
        when(subscriptionApiClient.getCurrentSubscription(TOKEN))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("unavailable"));

        assertThatThrownBy(() -> useCase.execute(PlansViewer.STUDENT, TOKEN))
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a catalog failure propagates untranslated and the subscription is not read")
    void catalogFailurePropagates() {
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.NotFoundException("no plans"));

        assertThatThrownBy(() -> useCase.execute(PlansViewer.STUDENT, TOKEN))
                .isInstanceOf(BillingApiClient.NotFoundException.class).hasMessage("no plans");
        verify(subscriptionApiClient, never()).getCurrentSubscription(anyString());
    }

    @Test
    @DisplayName("each plan gets the key of its own render, in a map keyed by plan id")
    void keysAreMappedByPlan() {
        when(billingApiClient.getPlans()).thenReturn(PLANS);
        when(subscriptionApiClient.getCurrentSubscription(TOKEN)).thenReturn(new CurrentSubscription.None());

        Map<String, String> keys = useCase.execute(PlansViewer.STUDENT, TOKEN).offer().idempotencyKeyByPlanId();

        assertThat(keys).containsEntry("plan-1", "key-1").containsEntry("plan-2", "key-2");
    }
}
