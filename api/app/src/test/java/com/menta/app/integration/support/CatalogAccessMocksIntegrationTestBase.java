package com.menta.app.integration.support;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base test class providing the {@code @MockitoBean} set and Spring profile shared by
 * {@code BillingPlansIntegrationTest}, {@code VirtualLessonAccessIntegrationTest}, and
 * {@code CatalogIntegrationTest} — previously duplicated verbatim in each class. See
 * {@code CapacityAdapterMocksIntegrationTestBase} for why a shared base (not just a matching mock
 * list) is what actually makes Spring treat these as cache-equal.
 *
 * <p>Extends the virtual domain's container, not billing's: 2 of the 3 fused classes already live
 * there, and a fused {@code ApplicationContext} needs exactly one {@code DataSource}. Which
 * physical container backs a class was always an assignment of convenience (see
 * {@code AbstractVirtualMySqlIntegrationTest}), not an architectural boundary, so
 * {@code BillingPlansIntegrationTest} moving containers here costs nothing.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
public abstract class CatalogAccessMocksIntegrationTestBase extends AbstractVirtualMySqlIntegrationTest {

    @MockitoBean protected AuthDegradedGuard authDegradedGuard;
    @MockitoBean protected TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean protected LoginRateLimitPort loginRateLimitPort;
    @MockitoBean protected ActivationRateLimitPort activationRateLimitPort;
    @MockitoBean protected PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockitoBean protected PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockitoBean protected BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockitoBean protected BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockitoBean protected ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    // ADR-0044: backed by a RedisTemplate this Redis-less context does not have.
    @MockitoBean protected CheckInRateLimitPort checkInRateLimitPort;
}
