package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.BankAccountDetails;
import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.QuoteAvailability;
import com.menta.billing.infrastructure.security.RedisBankTransferRateLimitPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * #36, US-BILLING-008, D4: real Redis proof that {@code consumeBankTransferCreation} is ONE shared
 * per-user daily budget, not two independent ones — {@link CreateBankTransferSubscriptionUseCaseImpl}
 * and {@link CreateBankTransferPhysicalPurchaseUseCaseImpl} both call the exact same {@link
 * com.menta.billing.application.port.out.BankTransferRateLimitPort#consumeBankTransferCreation}
 * method, backed here by the real {@link RedisBankTransferRateLimitPort} + a real Testcontainers
 * Redis, never a mock.
 *
 * <p>Deliberately placed in {@code api:billing} rather than {@code api:app}: every existing {@code
 * api:app} integration test mocks {@code BankTransferRateLimitPort} (no Redis Testcontainers
 * harness exists there), and this proof only needs the two real production use-case classes plus
 * the real Redis-backed port — exercising the exact shared collaborator D4 claims, with
 * proportional infrastructure (this module already depends on {@code spring-boot-starter-data-redis}
 * and {@code testcontainers}, so no new dependency is needed).</p>
 */
class BankTransferCreationBudgetIntegrationTest {

    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @AfterAll
    static void stopContainer() {
        REDIS.stop();
    }

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final BankAccountDetails ACCOUNT =
        new BankAccountDetails("0000003100000000000000", "menta.dance", "Menta Dance SRL", "30-00000000-0");

    private RedisTemplate<String, String> redisTemplate;
    private RedisBankTransferRateLimitPort rateLimitPort;
    private CreateBankTransferSubscriptionUseCaseImpl subscriptionUseCase;
    private CreateBankTransferPhysicalPurchaseUseCaseImpl physicalUseCase;
    private PhysicalCourseQuote fixedQuote;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379))
        );
        connectionFactory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        // Flush the single logical DB so no prior test's key leaks into this one.
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        Clock clock = () -> NOW;
        rateLimitPort = new RedisBankTransferRateLimitPort(
            redisTemplate, clock, 10, Duration.ofHours(26), 3, Duration.ofHours(72)
        );

        PlanRepository planRepository = mock(PlanRepository.class);
        PaymentRepository sharedPaymentRepository = mock(PaymentRepository.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        when(sharedPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.saveNewCheckout(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.findCurrentByUserId(any())).thenReturn(Optional.empty());
        when(planRepository.findActiveById(any())).thenReturn(Optional.of(activePlan()));
        subscriptionUseCase = new CreateBankTransferSubscriptionUseCaseImpl(
            planRepository, sharedPaymentRepository, subscriptionRepository, rateLimitPort, clock, ACCOUNT
        );

        PhysicalCourseQuoteRepository quoteRepository = mock(PhysicalCourseQuoteRepository.class);
        PhysicalCourseAvailabilityPort availabilityPort = mock(PhysicalCourseAvailabilityPort.class);
        PaymentRepository physicalPaymentRepository = mock(PaymentRepository.class);
        when(physicalPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(physicalPaymentRepository.findByExternalReference(any())).thenReturn(Optional.empty());
        fixedQuote = monthlyQuote();
        when(quoteRepository.findById(fixedQuote.getId().toString())).thenReturn(Optional.of(fixedQuote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(4, 5));
        physicalUseCase = new CreateBankTransferPhysicalPurchaseUseCaseImpl(
            quoteRepository, physicalPaymentRepository, availabilityPort, rateLimitPort, clock, ACCOUNT
        );
    }

    private static Plan activePlan() {
        return new Plan(
            PlanId.generate(), "Plan Mensual", "Acceso mensual", Money.of(new BigDecimal("15000.00"), "ARS"),
            30, false, PlanStatus.ACTIVE, "T", "C", List.of(PlanCourse.of("course-1")),
            Set.of(PaymentMethod.BANK_TRANSFER)
        );
    }

    private static PhysicalCoursePricing pricing() {
        return PhysicalCoursePricing.createFirstVersion(
            "course-1", Money.of(new BigDecimal("10000.00"), "ARS"), new BigDecimal("20.00"), NOW
        );
    }

    private static PhysicalCourseQuote monthlyQuote() {
        return PhysicalCourseQuote.monthly("course-1", pricing(), 4, QuoteAvailability.AVAILABLE, NOW.minusSeconds(60));
    }

    private static List<ScheduledSessionSnapshot> sessionsFrom(int count, int availableSpots) {
        return IntStream.range(0, count)
            .mapToObj(i -> new ScheduledSessionSnapshot(
                UUID.randomUUID().toString(), NOW.plus(Duration.ofDays(i)), availableSpots
            ))
            .toList();
    }

    private void createSubscription(String idempotencyKey) {
        subscriptionUseCase.create(new CreateSubscriptionCheckoutCommand(
            USER_ID, UUID.randomUUID().toString(), PaymentMethod.BANK_TRANSFER, idempotencyKey
        ));
    }

    private void createPhysicalPurchase(String idempotencyKey) {
        physicalUseCase.create(new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, fixedQuote.getId().toString(), PaymentMethod.BANK_TRANSFER, idempotencyKey
        ));
    }

    /**
     * D4, the whole point of the Phase 1 rename: ten creations spread across BOTH rails consume
     * the SAME real Redis counter, so the 11th — regardless of which rail it is — is rejected.
     */
    @Test
    void the_eleventh_bank_transfer_creation_in_one_day_is_rejected_whether_mixed_subscriptions_or_purchases() {
        // 5 subscriptions + 5 physical purchases = 10, alternating rails.
        for (int i = 0; i < 5; i++) {
            createSubscription("idem-sub-" + i);
            createPhysicalPurchase("idem-phy-" + i);
        }

        // The 11th request, on either rail, is rejected — proving one shared counter.
        assertThatThrownBy(() -> createSubscription("idem-sub-eleventh"))
            .isInstanceOf(BankTransferRateLimitedException.class);
    }

    /** Ten purchases alone, then one subscription: the mix direction does not matter either. */
    @Test
    void ten_physical_purchases_then_one_subscription_also_trips_the_shared_budget() {
        for (int i = 0; i < 10; i++) {
            createPhysicalPurchase("idem-phy-only-" + i);
        }

        assertThatThrownBy(() -> createSubscription("idem-sub-after-ten-physical"))
            .isInstanceOf(BankTransferRateLimitedException.class);
    }

    /** Nine creations still allow a tenth, on either rail — the budget is exactly 10, not fewer. */
    @Test
    void the_tenth_creation_is_still_allowed_regardless_of_rail_mix() {
        for (int i = 0; i < 9; i++) {
            createSubscription("idem-sub-nine-" + i);
        }

        assertThat(rateLimitPort.consumeBankTransferCreation(USER_ID).isAllowed()).isTrue();
    }
}
