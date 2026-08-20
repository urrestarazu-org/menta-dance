package com.menta.billing.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCapacityAssignmentPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.application.port.out.VirtualAccessGrantPort;
import com.menta.billing.application.port.out.WebhookInboxAppender;
import com.menta.billing.application.port.out.WebhookSignatureVerifier;
import com.menta.billing.application.usecase.GetPlanUseCaseImpl;
import com.menta.billing.application.usecase.ListPlansUseCaseImpl;
import com.menta.billing.application.usecase.PaymentVerificationService;
import com.menta.billing.infrastructure.security.RedisBillingPlansRateLimitPort;
import com.menta.billing.infrastructure.transaction.TransactionalReceiveWebhookUseCase;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;

class BillingConfigurationTest {

    private final Environment environment = mock(Environment.class);
    private final BillingConfiguration configuration = new BillingConfiguration(environment);

    @Test
    void wires_the_receive_webhook_use_case_bean_transactionally() {
        ReceiveWebhookUseCase useCase = configuration.receiveWebhookUseCase(
            mock(WebhookSignatureVerifier.class), mock(WebhookInboxAppender.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalReceiveWebhookUseCase.class);
    }

    @Test
    void wires_the_payment_verification_service_bean() {
        PaymentVerificationService service = configuration.paymentVerificationService(
            mock(PaymentRepository.class), mock(PaymentProviderPort.class), mock(PurchaseRepository.class),
            mock(SubscriptionRepository.class), mock(PhysicalCapacityAssignmentPort.class),
            mock(VirtualAccessGrantPort.class), mock(Clock.class)
        );

        assertThat(service).isInstanceOf(PaymentVerificationService.class);
    }

    @Test
    void wires_the_billing_plans_rate_limit_port_bean() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        BillingPlansRateLimitPort port = configuration.billingPlansRateLimitPort(redisTemplate, 60, 60);

        assertThat(port).isInstanceOf(RedisBillingPlansRateLimitPort.class);
    }

    @Test
    void wires_the_list_plans_use_case_bean() {
        ListPlansUseCase useCase = configuration.listPlansUseCase(
            mock(PlanRepository.class), mock(CourseCatalogPort.class), mock(BillingPlansRateLimitPort.class)
        );

        assertThat(useCase).isInstanceOf(ListPlansUseCaseImpl.class);
    }

    @Test
    void wires_the_get_plan_use_case_bean() {
        GetPlanUseCase useCase = configuration.getPlanUseCase(
            mock(PlanRepository.class), mock(CourseCatalogPort.class), mock(BillingPlansRateLimitPort.class)
        );

        assertThat(useCase).isInstanceOf(GetPlanUseCaseImpl.class);
    }

    @Test
    void validateWebhookSecretNotDefaultInProduction_passes_outside_production_profiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});

        configuration.validateWebhookSecretNotDefaultInProduction();
    }

    @Test
    void validateWebhookSecretNotDefaultInProduction_passes_in_production_with_a_custom_secret()
        throws NoSuchFieldException, IllegalAccessException {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"staging"});
        setWebhookHmacSecret(configuration, "a-real-production-secret");

        configuration.validateWebhookSecretNotDefaultInProduction();
    }

    @Test
    void validateWebhookSecretNotDefaultInProduction_rejects_the_dev_default_secret_in_production()
        throws NoSuchFieldException, IllegalAccessException {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        setWebhookHmacSecret(configuration, devDefaultSecret());

        assertThatThrownBy(configuration::validateWebhookSecretNotDefaultInProduction)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SECURITY");
    }

    private static String devDefaultSecret() throws NoSuchFieldException, IllegalAccessException {
        Field field = BillingConfiguration.class.getDeclaredField("DEV_DEFAULT_WEBHOOK_HMAC_SECRET");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static void setWebhookHmacSecret(BillingConfiguration configuration, String secret)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = BillingConfiguration.class.getDeclaredField("webhookHmacSecret");
        field.setAccessible(true);
        field.set(configuration, secret);
    }
}
