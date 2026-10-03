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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base test class providing the {@code @MockBean} set and Spring profile shared by
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

    @MockBean protected AuthDegradedGuard authDegradedGuard;
    @MockBean protected TokenBlacklistPort tokenBlacklistPort;
    @MockBean protected LoginRateLimitPort loginRateLimitPort;
    @MockBean protected ActivationRateLimitPort activationRateLimitPort;
    @MockBean protected PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean protected PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean protected BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean protected BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean protected ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
}
