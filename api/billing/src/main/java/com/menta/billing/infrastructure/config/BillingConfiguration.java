package com.menta.billing.infrastructure.config;

import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.application.port.in.GetPhysicalCoursePricingUseCase;
import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseOwnershipPort;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.PhysicalCoursePricingRevisionRepository;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.application.usecase.CreatePurchaseFromPaymentEventUseCase;
import com.menta.billing.application.usecase.MarkPurchaseExceptionUseCase;
import com.menta.billing.application.port.out.WebhookInboxAppender;
import com.menta.billing.application.port.out.WebhookSignatureVerifier;
import com.menta.billing.application.usecase.CancelSubscriptionUseCaseImpl;
import com.menta.billing.application.usecase.CreatePhysicalCourseQuoteUseCaseImpl;
import com.menta.billing.application.usecase.CreateSubscriptionCheckoutUseCaseImpl;
import com.menta.billing.application.usecase.GetPhysicalCoursePricingUseCaseImpl;
import com.menta.billing.application.usecase.GetPlanUseCaseImpl;
import com.menta.billing.application.usecase.ListPlansUseCaseImpl;
import com.menta.billing.application.usecase.PaymentVerificationService;
import com.menta.billing.application.usecase.PublishPhysicalPaymentCompletedUseCase;
import com.menta.billing.application.usecase.ReceiveWebhookUseCaseImpl;
import com.menta.billing.application.usecase.UpdatePhysicalCoursePricingUseCaseImpl;
import com.menta.billing.application.usecase.VirtualCourseEntitlementService;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.billing.infrastructure.security.RedisBillingPlansRateLimitPort;
import com.menta.billing.infrastructure.transaction.TransactionalCancelSubscriptionUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalCreateSubscriptionCheckoutUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalReceiveWebhookUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalUpdatePhysicalCoursePricingUseCase;
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
    private static final String LOCAL_MERCADO_PAGO_PROFILE = "e2e-mercadopago";

    private final Environment environment;

    @Value("${billing.webhook.mercadopago.hmac-secret:" + DEV_DEFAULT_WEBHOOK_HMAC_SECRET + "}")
    private String webhookHmacSecret;

    public BillingConfiguration(Environment environment) {
        this.environment = environment;
    }

    /** Fail-fast: reject the dev-only webhook HMAC secret in production profiles (US-BILLING-002). */
    @PostConstruct
    void validateWebhookSecretNotDefaultInProduction() {
        if (isProductionProfile() && isLocalMercadoPagoProfileActive()) {
            throw new IllegalStateException("SECURITY: local Mercado Pago simulation cannot run in a production profile");
        }
        if (isProductionProfile() && DEV_DEFAULT_WEBHOOK_HMAC_SECRET.equals(webhookHmacSecret)) {
            throw new IllegalStateException(
                "SECURITY: production requires a non-default Mercado Pago webhook HMAC secret. "
                    + "Set billing.webhook.mercadopago.hmac-secret via environment variables. Active profiles: "
                    + String.join(", ", environment.getActiveProfiles())
            );
        }
    }

    private boolean isLocalMercadoPagoProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if (LOCAL_MERCADO_PAGO_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
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
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock,
        PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase
    ) {
        return new PaymentVerificationService(
            paymentRepository, paymentProviderPort, subscriptionRepository, planRepository, clock,
            publishPhysicalPaymentCompletedUseCase
        );
    }

    /** Task TASK-003: produces the {@code billing.PhysicalPaymentCompleted}
     *  outbox event from a completed physical payment. */
    @Bean
    public PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase(
        com.menta.billing.application.port.out.BillingOutboxAppenderPort billingOutboxAppenderPort
    ) {
        return new PublishPhysicalPaymentCompletedUseCase(billingOutboxAppenderPort);
    }

    /** ADR-0039: Virtual reads Billing's current subscription entitlement. */
    @Bean
    public VirtualCourseEntitlementPort virtualCourseEntitlementPort(
        PlanRepository planRepository, SubscriptionRepository subscriptionRepository, Clock clock
    ) {
        return new VirtualCourseEntitlementService(planRepository, subscriptionRepository, clock);
    }

    /**
     * US-BILLING-010. Wrapped in a transactional decorator, unlike {@code
     * createPhysicalCourseQuoteUseCase}: a checkout writes a Payment, a
     * Subscription and its preference, and a rejected one must leave none of
     * them behind.
     *
     * <p>{@code merchantAccountId} is configuration, never client input — it
     * is the value {@code Payment.matchesExpected} will later demand from the
     * provider's response.</p>
     */
    @Bean
    public CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase(
        PlanRepository planRepository, PaymentRepository paymentRepository,
        SubscriptionRepository subscriptionRepository, PaymentPreferencePort paymentPreferencePort, Clock clock,
        @Value("${billing.mercadopago.merchant-account-id:}") String merchantAccountId
    ) {
        return new TransactionalCreateSubscriptionCheckoutUseCase(new CreateSubscriptionCheckoutUseCaseImpl(
            planRepository, paymentRepository, subscriptionRepository, paymentPreferencePort, clock,
            merchantAccountId
        ));
    }

    /**
     * US-BILLING-011. Wrapped the same way as {@code createSubscriptionCheckoutUseCase}: a
     * single write today, kept transactional so a caller never observes a partial cancellation.
     */
    @Bean
    public CancelSubscriptionUseCase cancelSubscriptionUseCase(
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock
    ) {
        return new TransactionalCancelSubscriptionUseCase(
            new CancelSubscriptionUseCaseImpl(subscriptionRepository, planRepository, clock)
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

    @Bean
    public GetPhysicalCoursePricingUseCase getPhysicalCoursePricingUseCase(
        PhysicalCoursePricingRepository pricingRepository
    ) {
        return new GetPhysicalCoursePricingUseCaseImpl(pricingRepository);
    }

    @Bean
    public UpdatePhysicalCoursePricingUseCase updatePhysicalCoursePricingUseCase(
        PhysicalCourseOwnershipPort physicalCourseOwnershipPort, PhysicalCoursePricingRepository pricingRepository,
        PhysicalCoursePricingRevisionRepository revisionRepository, Clock clock
    ) {
        return new TransactionalUpdatePhysicalCoursePricingUseCase(new UpdatePhysicalCoursePricingUseCaseImpl(
            physicalCourseOwnershipPort, pricingRepository, revisionRepository, clock
        ));
    }

    /**
     * No {@code Transactional*UseCase} decorator here, unlike {@code
     * updatePhysicalCoursePricingUseCase} above — a quote is a single
     * immutable insert, not two writes that must commit atomically
     * (US-BILLING-006).
     */
    @Bean
    public CreatePhysicalCourseQuoteUseCase createPhysicalCourseQuoteUseCase(
        PhysicalCoursePricingRepository pricingRepository, PhysicalCourseAvailabilityPort availabilityPort,
        PhysicalCourseQuoteRepository quoteRepository, Clock clock
    ) {
        return new CreatePhysicalCourseQuoteUseCaseImpl(pricingRepository, availabilityPort, quoteRepository, clock);
    }

    /** Task TASK-004: idempotently upserts a {@code Purchase(PENDING_FULFILLMENT)}
     *  keyed on paymentId when an outbox event arrives. */
    @Bean
    public CreatePurchaseFromPaymentEventUseCase createPurchaseFromPaymentEventUseCase(
        PurchaseRepository purchaseRepository
    ) {
        return new CreatePurchaseFromPaymentEventUseCase(purchaseRepository);
    }

    /** Task TASK-004: state-machine guard for the {@code EXCEPTION} terminal
     *  residual (refuses {@code ASSIGNED → EXCEPTION}). */
    @Bean
    public MarkPurchaseExceptionUseCase markPurchaseExceptionUseCase(
        PurchaseRepository purchaseRepository
    ) {
        return new MarkPurchaseExceptionUseCase(purchaseRepository);
    }
}
