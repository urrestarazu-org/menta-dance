package com.menta.billing.infrastructure.config;

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
import com.menta.billing.application.usecase.ReceiveWebhookUseCaseImpl;
import com.menta.billing.infrastructure.security.RedisBillingPlansRateLimitPort;
import com.menta.billing.infrastructure.transaction.TransactionalReceiveWebhookUseCase;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Wires the plans and payment-webhook use cases. Adapter classes ({@code
 * PlanRepositoryAdapter}, {@code NotImplementedCourseCatalogPort}, {@code
 * PaymentRepositoryAdapter}, etc.) are {@code @Component}-scanned; the use
 * cases are plain Java classes composed here, mirroring {@code
 * AuthConfiguration}'s rationale: calling use cases directly from
 * controllers keeps port dependencies visible at the boundary instead of
 * implicit {@code @Autowired} on use-case classes.
 */
@Configuration
public class BillingConfiguration {

    /** Dev-only default HMAC secret — detects insecure configuration in production, same criterion as auth.jwt.base64-secret. */
    private static final String DEV_DEFAULT_WEBHOOK_HMAC_SECRET =
        "ZGV2LW9ubHktd2ViaG9vay1zZWNyZXQtbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==";

    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "staging");

    private final Environment environment;

    @Value("${billing.webhook.mercadopago.hmac-secret:" + DEV_DEFAULT_WEBHOOK_HMAC_SECRET + "}")
    private String webhookHmacSecret;

    public BillingConfiguration(Environment environment) {
        this.environment = environment;
    }

    /** Fail-fast: reject the dev-only webhook HMAC secret in production profiles (US-BILLING-002). */
    @PostConstruct
    void validateWebhookSecretNotDefaultInProduction() {
        if (isProductionProfile() && DEV_DEFAULT_WEBHOOK_HMAC_SECRET.equals(webhookHmacSecret)) {
            throw new IllegalStateException(
                "SECURITY: production requires a non-default Mercado Pago webhook HMAC secret. "
                    + "Set billing.webhook.mercadopago.hmac-secret via environment variables. Active profiles: "
                    + String.join(", ", environment.getActiveProfiles())
            );
        }
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (PRODUCTION_PROFILES.contains(profile.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public ReceiveWebhookUseCase receiveWebhookUseCase(
        WebhookSignatureVerifier signatureVerifier, WebhookInboxAppender inboxAppender, Clock clock
    ) {
        return new TransactionalReceiveWebhookUseCase(
            new ReceiveWebhookUseCaseImpl(signatureVerifier, inboxAppender, clock, webhookHmacSecret)
        );
    }

    @Bean
    public PaymentVerificationService paymentVerificationService(
        PaymentRepository paymentRepository, PaymentProviderPort paymentProviderPort,
        PurchaseRepository purchaseRepository, SubscriptionRepository subscriptionRepository,
        PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort,
        VirtualAccessGrantPort virtualAccessGrantPort, Clock clock
    ) {
        return new PaymentVerificationService(
            paymentRepository, paymentProviderPort, purchaseRepository, subscriptionRepository,
            physicalCapacityAssignmentPort, virtualAccessGrantPort, clock
        );
    }

    @Bean
    public BillingPlansRateLimitPort billingPlansRateLimitPort(
        RedisTemplate<String, String> redisTemplate,
        @Value("${billing.plans.rate-limit.max-requests:60}") long maxRequests,
        @Value("${billing.plans.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        return new RedisBillingPlansRateLimitPort(redisTemplate, maxRequests, Duration.ofSeconds(windowSeconds));
    }

    @Bean
    public ListPlansUseCase listPlansUseCase(
        PlanRepository planRepository, CourseCatalogPort courseCatalogPort,
        BillingPlansRateLimitPort billingPlansRateLimitPort
    ) {
        return new ListPlansUseCaseImpl(planRepository, courseCatalogPort, billingPlansRateLimitPort);
    }

    @Bean
    public GetPlanUseCase getPlanUseCase(
        PlanRepository planRepository, CourseCatalogPort courseCatalogPort,
        BillingPlansRateLimitPort billingPlansRateLimitPort
    ) {
        return new GetPlanUseCaseImpl(planRepository, courseCatalogPort, billingPlansRateLimitPort);
    }
}
