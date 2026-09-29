package com.menta.app.integration.physical;

import com.menta.app.integration.support.AbstractPhysicalMySqlIntegrationTest;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base test class providing the {@code @MockBean} set and Spring profile shared by
 * {@link AssignCapacityAdapterIntegrationTest} and {@link HoldCapacityAdapterIntegrationTest} —
 * previously duplicated verbatim in each class. Spring's context cache key includes each
 * {@code @MockBean}'s declaring field, not just its type — so two classes with structurally
 * identical mocks are only cache-equal when they inherit the same field declarations from a
 * shared base, as here, rather than redeclaring their own (same root cause as
 * {@code AbstractPhysicalMySqlIntegrationTest}'s {@code @DynamicPropertySource} note).
 *
 * <p>{@code @SpringBootTest}/{@code @ActiveProfiles} live here only, not on the subclasses:
 * Spring resolves them by "closest wins" up the hierarchy, so a subclass that declares none picks
 * up this base's.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
abstract class CapacityAdapterMocksIntegrationTestBase extends AbstractPhysicalMySqlIntegrationTest {

    @MockBean protected AuthDegradedGuard authDegradedGuard;
    @MockBean protected TokenBlacklistPort tokenBlacklistPort;
    @MockBean protected LoginRateLimitPort loginRateLimitPort;
    @MockBean protected ActivationRateLimitPort activationRateLimitPort;
    @MockBean protected PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean protected PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean protected BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean protected BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean protected CourseCatalogPort courseCatalogPort;
    @MockBean protected PaymentProviderPort paymentProviderPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    protected RedisTemplate redisTemplate;
}
