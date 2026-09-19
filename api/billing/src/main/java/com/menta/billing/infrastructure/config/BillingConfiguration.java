package com.menta.billing.infrastructure.config;

import com.menta.billing.application.dto.BankAccountDetails;
import com.menta.billing.application.port.in.AssignTrialSubscriptionUseCase;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import com.menta.billing.application.port.in.CreateBankTransferSubscriptionUseCase;
import com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.application.port.in.GetCurrentSubscriptionUseCase;
import com.menta.billing.application.port.in.GetPhysicalCoursePricingUseCase;
import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.in.GetSubscriptionHistoryUseCase;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProofNotificationPort;
import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.application.port.out.PaymentProofStoragePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCapacityHoldPort;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseOwnershipPort;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.PhysicalCoursePricingRevisionRepository;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.application.port.out.WebhookInboxAppender;
import com.menta.billing.application.port.out.WebhookSignatureVerifier;
import com.menta.billing.application.usecase.AssignTrialSubscriptionUseCaseImpl;
import com.menta.billing.application.usecase.CancelSubscriptionUseCaseImpl;
import com.menta.billing.application.usecase.CreateBankTransferSubscriptionUseCaseImpl;
import com.menta.billing.application.usecase.CreatePhysicalCourseQuoteUseCaseImpl;
import com.menta.billing.application.usecase.CreatePhysicalPurchaseCheckoutUseCaseImpl;
import com.menta.billing.application.usecase.CreatePurchaseFromPaymentEventUseCase;
import com.menta.billing.application.usecase.CreateSubscriptionCheckoutUseCaseImpl;
import com.menta.billing.application.usecase.GetCurrentSubscriptionUseCaseImpl;
import com.menta.billing.application.usecase.GetPhysicalCoursePricingUseCaseImpl;
import com.menta.billing.application.usecase.GetPlanUseCaseImpl;
import com.menta.billing.application.usecase.GetSubscriptionHistoryUseCaseImpl;
import com.menta.billing.application.usecase.ListPlansUseCaseImpl;
import com.menta.billing.application.usecase.MarkPurchaseAssignedUseCase;
import com.menta.billing.application.usecase.MarkPurchaseExceptionUseCase;
import com.menta.billing.application.usecase.PaymentFulfillmentService;
import com.menta.billing.application.usecase.PaymentVerificationService;
import com.menta.billing.application.usecase.PublishPaymentFulfillmentFailedUseCase;
import com.menta.billing.application.usecase.PublishPhysicalPaymentCompletedUseCase;
import com.menta.billing.application.usecase.ReceiveWebhookUseCaseImpl;
import com.menta.billing.application.usecase.RoutingCreateSubscriptionCheckoutUseCase;
import com.menta.billing.application.usecase.SubmitPaymentProofUseCaseImpl;
import com.menta.billing.application.usecase.UpdatePhysicalCoursePricingUseCaseImpl;
import com.menta.billing.application.usecase.VirtualCourseEntitlementService;
import com.menta.billing.domain.service.PaymentProofContentValidator;
import com.menta.billing.infrastructure.security.RedisBankTransferRateLimitPort;
import com.menta.billing.infrastructure.security.RedisBillingPlansRateLimitPort;
import com.menta.billing.infrastructure.transaction.TransactionalAssignTrialSubscriptionUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalCancelSubscriptionUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalCreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalCreateSubscriptionCheckoutUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalReceiveWebhookUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalSubmitPaymentProofUseCase;
import com.menta.billing.infrastructure.transaction.TransactionalUpdatePhysicalCoursePricingUseCase;
import com.menta.shared.auth.UserExistencePort;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
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

    /**
     * Design C10: the collaborator an approved bank-transfer payment (P4) and the 72h expiry
     * sweep (P5) will share with {@code PaymentVerificationService}, so all three settle a
     * subscription through exactly the same code an approved Mercado Pago payment already does.
     */
    @Bean
    public PaymentFulfillmentService paymentFulfillmentService(
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository, Clock clock,
        PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase
    ) {
        return new PaymentFulfillmentService(
            subscriptionRepository, planRepository, clock, publishPhysicalPaymentCompletedUseCase
        );
    }

    @Bean
    public PaymentVerificationService paymentVerificationService(
        PaymentRepository paymentRepository, PaymentProviderPort paymentProviderPort, Clock clock,
        PaymentFulfillmentService paymentFulfillmentService
    ) {
        return new PaymentVerificationService(
            paymentRepository, paymentProviderPort, clock, paymentFulfillmentService
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
     *
     * <p>Design D3: the transactional decorator now wraps {@link RoutingCreateSubscriptionCheckoutUseCase},
     * not {@link CreateSubscriptionCheckoutUseCaseImpl} directly, so {@code
     * CreateSubscriptionCheckoutUseCaseImpl} is reached byte-identically for {@code MERCADO_PAGO}
     * and {@code BANK_TRANSFER} routes to {@code createBankTransferSubscriptionUseCase} — one
     * all-or-nothing write boundary regardless of which delegate the router picks.</p>
     */
    @Bean
    public CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase(
        PlanRepository planRepository, PaymentRepository paymentRepository,
        SubscriptionRepository subscriptionRepository, PaymentPreferencePort paymentPreferencePort, Clock clock,
        @Value("${billing.mercadopago.merchant-account-id:}") String merchantAccountId,
        CreateBankTransferSubscriptionUseCase createBankTransferSubscriptionUseCase
    ) {
        CreateSubscriptionCheckoutUseCase mercadoPagoUseCase = new CreateSubscriptionCheckoutUseCaseImpl(
            planRepository, paymentRepository, subscriptionRepository, paymentPreferencePort, clock,
            merchantAccountId
        );
        return new TransactionalCreateSubscriptionCheckoutUseCase(new RoutingCreateSubscriptionCheckoutUseCase(
            mercadoPagoUseCase, createBankTransferSubscriptionUseCase
        ));
    }

    /**
     * US-BILLING-003 escenario 1 (design D3). Not wrapped in its own transactional decorator —
     * only ever reached through {@code createSubscriptionCheckoutUseCase}'s router, which already
     * wraps the whole dispatch transactionally, mirroring how {@code
     * createPhysicalCourseQuoteUseCase} skips its own decorator for the same reason: a second,
     * inner boundary here would be redundant, not safer.
     *
     * <p>{@code bankAccountDetails} carries the academy's one configured CBU (design C2) — the
     * value {@link com.menta.billing.domain.model.Payment#awaitingManualVerification} sets as
     * {@code expectedMerchantAccountId}.</p>
     */
    @Bean
    public CreateBankTransferSubscriptionUseCase createBankTransferSubscriptionUseCase(
        PlanRepository planRepository, PaymentRepository paymentRepository,
        SubscriptionRepository subscriptionRepository, BankTransferRateLimitPort bankTransferRateLimitPort,
        Clock clock,
        @Value("${billing.bank-transfer.account.cbu:}") String cbu,
        @Value("${billing.bank-transfer.account.alias:}") String alias,
        @Value("${billing.bank-transfer.account.holder:}") String holder,
        @Value("${billing.bank-transfer.account.cuit:}") String cuit
    ) {
        return new CreateBankTransferSubscriptionUseCaseImpl(
            planRepository, paymentRepository, subscriptionRepository, bankTransferRateLimitPort, clock,
            new BankAccountDetails(cbu, alias, holder, cuit)
        );
    }

    /**
     * Design C7: two independent Redis-backed budgets — 10 bank-transfer subscription
     * creations/user/day (consumed by {@code createBankTransferSubscriptionUseCase} above) and 3
     * proof uploads/payment/72h (consumed by {@code SubmitPaymentProofUseCaseImpl}, wired in a
     * later phase). Declared as one bean now so the port is complete from this phase.
     */
    @Bean
    public BankTransferRateLimitPort bankTransferRateLimitPort(
        RedisTemplate<String, String> redisTemplate, Clock clock,
        @Value("${billing.bank-transfer.rate-limit.subscription-creation.max-requests:10}")
            long subscriptionCreationMaxRequests,
        @Value("${billing.bank-transfer.rate-limit.subscription-creation.window-hours:26}")
            long subscriptionCreationWindowHours,
        @Value("${billing.bank-transfer.rate-limit.proof-upload.max-requests:3}") long proofUploadMaxRequests,
        @Value("${billing.bank-transfer.rate-limit.proof-upload.window-hours:72}") long proofUploadWindowHours
    ) {
        return new RedisBankTransferRateLimitPort(
            redisTemplate, clock, subscriptionCreationMaxRequests, Duration.ofHours(subscriptionCreationWindowHours),
            proofUploadMaxRequests, Duration.ofHours(proofUploadWindowHours)
        );
    }

    /**
     * US-BILLING-003 escenario 2 (design C12). Wrapped transactionally: the previous blob's key is
     * read, the new proof row is saved and the notification is sent all inside one transaction, so
     * a failed notification (design C12 step 6) rolls back the row instead of leaving a proof
     * persisted that nobody was told about. {@code PaymentProofRepositoryAdapter} and {@code
     * LocalFilesystemPaymentProofStorageAdapter} self-register as {@code @Component}s (P3a/P3b),
     * resolved here by type like every other adapter in this configuration.
     */
    @Bean
    public SubmitPaymentProofUseCase submitPaymentProofUseCase(
        PaymentRepository paymentRepository, PaymentProofRepository paymentProofRepository,
        PaymentProofStoragePort paymentProofStoragePort, PaymentProofNotificationPort paymentProofNotificationPort,
        BankTransferRateLimitPort bankTransferRateLimitPort, Clock clock
    ) {
        return new TransactionalSubmitPaymentProofUseCase(new SubmitPaymentProofUseCaseImpl(
            paymentRepository, paymentProofRepository, paymentProofStoragePort, paymentProofNotificationPort,
            bankTransferRateLimitPort, new PaymentProofContentValidator(), clock
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

    /**
     * US-BILLING-012. Wrapped the same way as {@code cancelSubscriptionUseCase}: the slot claim
     * and the course snapshot (design A12) must commit together. {@code UserExistencePort} is
     * the D8 cross-module port — {@code auth}'s {@code UserExistenceAdapter} resolves it via
     * component scan alone.
     */
    @Bean
    public AssignTrialSubscriptionUseCase assignTrialSubscriptionUseCase(
        SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
        UserExistencePort userExistencePort, Clock clock
    ) {
        return new TransactionalAssignTrialSubscriptionUseCase(
            new AssignTrialSubscriptionUseCaseImpl(subscriptionRepository, planRepository, userExistencePort, clock)
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

    /**
     * US-BILLING-004. No {@code Transactional*} decorator, mirroring {@code listPlansUseCase} /
     * {@code getPlanUseCase}: a read-only query, already covered by the adapter's own per-method
     * {@code readOnly = true}.
     */
    @Bean
    public GetCurrentSubscriptionUseCase getCurrentSubscriptionUseCase(
        SubscriptionRepository subscriptionRepository, Clock clock
    ) {
        return new GetCurrentSubscriptionUseCaseImpl(subscriptionRepository, clock);
    }

    /** US-BILLING-004. Same rationale as {@code getCurrentSubscriptionUseCase} above. */
    @Bean
    public GetSubscriptionHistoryUseCase getSubscriptionHistoryUseCase(
        SubscriptionRepository subscriptionRepository
    ) {
        return new GetSubscriptionHistoryUseCaseImpl(subscriptionRepository);
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

    /**
     * #41, US-PHYSICAL-004. No {@code Transactional*UseCase} decorator here, same rationale as
     * {@code createPhysicalCourseQuoteUseCase}: a single {@code Payment} write, not two writes
     * that must commit atomically — unlike {@code createSubscriptionCheckoutUseCase}, this
     * checkout creates no second local aggregate (the {@code Purchase} is created later, from the
     * confirmed webhook).
     *
     * <p>#208 (design B5): the hold TTL is read here as {@code
     * billing.physical.capacity.hold.ttl-ms}, deliberately NOT the {@code
     * physical.capacity.hold.ttl-ms} key design.md names — that key belongs
     * to Physical's own module config (its expiry sweep, Phase 9, not yet
     * wired) and this checkout use case cannot depend on {@code api:physical}
     * to read it (ArchUnit boundary, D4). The port's {@code hold} contract
     * already requires the caller to compute {@code expiresAt} itself, so
     * billing owns its own copy of the same 30-minute default (B5) until
     * Phase 9 lands; the two keys are expected to converge onto one value in
     * practice, never to drift, since both express the same design decision.</p>
     */
    @Bean
    public CreatePhysicalPurchaseCheckoutUseCase createPhysicalPurchaseCheckoutUseCase(
        PhysicalCourseQuoteRepository quoteRepository, PaymentRepository paymentRepository,
        PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort, PhysicalCapacityHoldPort physicalCapacityHoldPort,
        PaymentPreferencePort paymentPreferencePort, Clock clock,
        @Value("${billing.mercadopago.merchant-account-id:}") String merchantAccountId,
        @Value("${billing.physical.capacity.hold.ttl-ms:1800000}") long holdTtlMs
    ) {
        return new TransactionalCreatePhysicalPurchaseCheckoutUseCase(new CreatePhysicalPurchaseCheckoutUseCaseImpl(
            quoteRepository, paymentRepository, physicalCourseAvailabilityPort, physicalCapacityHoldPort,
            paymentPreferencePort, clock, merchantAccountId, Duration.ofMillis(holdTtlMs)
        ));
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
        PurchaseRepository purchaseRepository, PaymentRepository paymentRepository,
        BillingOutboxAppenderPort outboxAppender, Clock clock
    ) {
        return new MarkPurchaseExceptionUseCase(purchaseRepository, paymentRepository, outboxAppender, clock);
    }

    /** #41 PR8: the mirror image of {@code markPurchaseExceptionUseCase} for the success branch (design A3). */
    @Bean
    public MarkPurchaseAssignedUseCase markPurchaseAssignedUseCase(
        PurchaseRepository purchaseRepository
    ) {
        return new MarkPurchaseAssignedUseCase(purchaseRepository);
    }

    /**
     * #209 Phase B (design C1/C2, D10): the payment-level fallback for the
     * three pre-{@code Purchase} sites in {@code
     * PhysicalCapacityAssignmentOutboxEventHandler}. {@code REQUIRES_NEW}
     * is declared on the use case itself, not this bean wiring.
     */
    @Bean
    public PublishPaymentFulfillmentFailedUseCase publishPaymentFulfillmentFailedUseCase(
        BillingOutboxAppenderPort outboxAppender, Clock clock
    ) {
        return new PublishPaymentFulfillmentFailedUseCase(outboxAppender, clock);
    }
}
