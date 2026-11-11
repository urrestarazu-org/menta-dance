package com.menta.app.integration.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractAuthMySqlIntegrationTest;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import com.menta.shared.domain.vo.Email;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared setup for the user administration integration tests (#378): real MySQL (Testcontainers), the real outbox
 * and reconciler, and the same in-memory stand-in for the Redis token-version projection that
 * {@code AuthRevocationIntegrationTest} uses, since the {@code integration-test} profile has no Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
abstract class AbstractAdminUserIntegrationTest extends AbstractAuthMySqlIntegrationTest {

    static final String RAW_PASSWORD = "SecurePass123!";

    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected OutboxRowJpaRepository outboxRowJpaRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;

    @MockitoBean private AuthDegradedGuard authDegradedGuard;
    @MockitoBean private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean private LoginRateLimitPort loginRateLimitPort;
    @MockitoBean private ActivationRateLimitPort activationRateLimitPort;
    @MockitoBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockitoBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockitoBean private com.menta.billing.application.port.out.BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockitoBean private com.menta.billing.application.port.out.BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockitoBean
    private com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    @MockitoBean private CheckInRateLimitPort checkInRateLimitPort;

    /** What the Redis projection would hold, by user id. */
    protected final Map<String, Long> projectedVersions = new ConcurrentHashMap<>();
    private final Set<String> blacklistedJtis = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void cleanDatabaseAndWireFakes() {
        // The MySQL schema is shared by every test of the context: every test starts from an empty user table so
        // the "at least one active admin" rule only ever sees the users the test created.
        jdbcTemplate.update("DELETE FROM auth_user_audit");
        jdbcTemplate.update("DELETE FROM auth_activation_tokens");
        jdbcTemplate.update("DELETE FROM auth_refresh_tokens");
        jdbcTemplate.update("DELETE FROM auth_users");
        outboxRowJpaRepository.deleteAll();

        when(authDegradedGuard.isDegraded()).thenReturn(false);
        when(loginRateLimitPort.check(anyString(), anyString())).thenReturn(RateLimitDecision.allowed());
        lenient().when(activationRateLimitPort.consume(anyString(), any())).thenReturn(RateLimitDecision.allowed());
        projectedVersions.clear();
        blacklistedJtis.clear();
        lenient().when(tokenBlacklistPort.currentTokenVersion(anyString())).thenAnswer(invocation -> {
            Long version = projectedVersions.get((String) invocation.getArgument(0));
            return version == null ? OptionalLong.empty() : OptionalLong.of(version);
        });
        lenient().doAnswer(invocation -> {
            // Mirrors the production Lua script's monotonic guard: a lower projection never wins.
            projectedVersions.merge(invocation.getArgument(0), invocation.<Long>getArgument(1), Math::max);
            return null;
        }).when(tokenBlacklistPort).projectTokenVersion(anyString(), anyLong());
        lenient().when(tokenBlacklistPort.isBlacklisted(anyString()))
            .thenAnswer(invocation -> blacklistedJtis.contains((String) invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            blacklistedJtis.add(invocation.getArgument(0));
            return null;
        }).when(tokenBlacklistPort).blacklist(anyString(), any(Duration.class));
    }

    /** Persists an already-active user with the shared test password. */
    protected User newUser(Role role) {
        String email = role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com";
        return userRepository.save(User.create(Email.of(email), passwordEncoder.encode(RAW_PASSWORD), role));
    }
}
