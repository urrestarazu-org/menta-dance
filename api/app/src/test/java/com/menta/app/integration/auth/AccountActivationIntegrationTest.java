package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.app.outbox.ActivationOutboxEventHandler;
import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.ActivationNotificationPort;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.exception.InvalidCredentialsException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL-backed integration coverage for the public account-activation flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class AccountActivationIntegrationTest {

    private static final String EMAIL = "activation.student@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private static final String RAW_TOKEN = "integration-activation-token";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private RegisterUserUseCase registerUserUseCase;
    @Autowired private ActivateAccountUseCase activateAccountUseCase;
    @Autowired private LoginUseCase loginUseCase;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivationTokenJpaRepository activationTokenRepository;
    @Autowired private OutboxRowJpaRepository outboxRowJpaRepository;

    @MockBean private ActivationTokenGenerator activationTokenGenerator;
    @MockBean private ActivationTokenHasher activationTokenHasher;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private ActivationNotificationPort activationNotificationPort;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;

    @AfterEach
    void cleanUp() {
        outboxRowJpaRepository.deleteAll();
        activationTokenRepository.deleteAll();
        userRepository.findByEmail(Email.of(EMAIL)).ifPresent(user -> userRepository.deleteById(user.getId()));
    }

    @Test
    void registrationDeliveryActivationAndLoginFormOneDurableFlow() {
        allowRegistration();
        registerUserUseCase.register(new RegisterUserCommand(EMAIL, PASSWORD, Role.STUDENT, "client-fingerprint"));

        assertThat(userRepository.findByEmail(Email.of(EMAIL))).get()
            .extracting(user -> user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        assertThat(outboxRows).singleElement()
            .extracting(OutboxRowJpaEntity::getEventType).isEqualTo("auth.AccountActivationRequested");

        new ActivationOutboxEventHandler(activationNotificationPort).handle(outboxRows.get(0));
        verify(activationNotificationPort).sendActivationEmail(any());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD)))
            .isInstanceOf(InvalidCredentialsException.class);
        activateAccountUseCase.activate(new ActivateAccountCommand(RAW_TOKEN));
        assertThat(loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD)).accessToken()).isNotBlank();
    }

    @Test
    void tokenPersistenceFailureRollsBackUserTokenAndOutbox() {
        allowRegistration();
        // A duplicate generated hash is rejected by MySQL; no partial public registration may survive.
        activationTokenRepository.save(new com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "0".repeat(64), new byte[] {1},
            new byte[12], (short) 1, java.time.Instant.now().plusSeconds(60), java.time.Instant.now(), null, null
        ));
        when(activationTokenGenerator.generate()).thenReturn("collision-token");

        // This asserts the transactional boundary with a forced persistence failure.
        assertThatThrownBy(() -> registerUserUseCase.register(
            new RegisterUserCommand(EMAIL, PASSWORD, Role.STUDENT, "client-fingerprint")
        )).isInstanceOf(RuntimeException.class);
        assertThat(userRepository.findByEmail(Email.of(EMAIL))).isEmpty();
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    @Test
    void concurrentDoubleActivationAllowsExactlyOneTransition() throws Exception {
        allowRegistration();
        registerUserUseCase.register(new RegisterUserCommand(EMAIL, PASSWORD, Role.STUDENT, "client-fingerprint"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> activate = () -> {
                try {
                    activateAccountUseCase.activate(new ActivateAccountCommand(RAW_TOKEN));
                    return true;
                } catch (ActivationTokenInvalidException expected) {
                    return false;
                }
            };
            List<Future<Boolean>> outcomes = executor.invokeAll(List.of(activate, activate));
            assertThat(outcomes.stream().filter(future -> get(future)).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void allowRegistration() {
        when(activationRateLimitPort.consume(any(), any())).thenReturn(RateLimitDecision.allowed());
        when(activationTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(activationTokenHasher.hash(RAW_TOKEN)).thenReturn("0".repeat(64));
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
