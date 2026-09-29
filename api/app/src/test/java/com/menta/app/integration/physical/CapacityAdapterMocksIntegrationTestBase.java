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
 * {@link AssignCapacityAdapterIntegrationTest} and {@link HoldCapacityAdapterIntegrationTest}.
 *
 * <p><b>Why this class exists — not just DRY.</b> Before it, both classes redeclared the exact
 * same 11 {@code @MockBean} fields plus the exact same {@code @SpringBootTest}/
 * {@code @ActiveProfiles}.
 * Spring's {@code TestContext} framework caches a live {@code ApplicationContext} keyed on
 * everything that could change its shape — active profile, {@code @DynamicPropertySource}
 * customizers, and every {@code @MockBean}. Crucially, a {@code @MockBean} is identified by the
 * {@link java.lang.reflect.Field} that declares it, not by its type: two mock fields of the exact
 * same type in two different classes are two different cache-key entries unless both classes
 * inherit the field from the same declaring class. So even though these two test classes'
 * redeclared mocks were byte-for-byte identical in type and count, Spring could never treat them
 * as the same context — each class paid to build and tear down its own full
 * {@code ApplicationContext} from scratch, doubling Spring Boot startup cost, database schema
 * creation (see {@link com.menta.app.integration.support.MySqlSchemas}), and everything else that
 * scales with live context count.</p>
 *
 * <p>Moving the 11 {@code @MockBean} fields here means both subclasses inherit the exact same
 * {@link java.lang.reflect.Field} objects — at that point Spring's cache key genuinely matches,
 * one {@code ApplicationContext} is built once and reused for every test in both classes, and the
 * two classes run in a fraction of the time. This mirrors, at the mock layer, the same structural
 * mistake fixed at the container layer by
 * {@link com.menta.app.integration.support.AbstractPhysicalMySqlIntegrationTest}: sharing state
 * correctly requires the JVM to see one physical declaration, not several structurally-identical
 * copies.</p>
 *
 * <p>{@code @SpringBootTest}/{@code @ActiveProfiles} live here only, not on the subclasses, for
 * the same reason: Spring resolves these annotations by "closest wins" up the class hierarchy
 * rather than merging them, so a subclass that declares neither inherits exactly this base's —
 * removing any chance of the two subclasses silently drifting into slightly different
 * configurations (and, as a side effect, a different cache key) over time.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
abstract class CapacityAdapterMocksIntegrationTestBase
    extends AbstractPhysicalMySqlIntegrationTest {

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
