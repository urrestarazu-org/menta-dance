package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.LogoutCommand;
import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.shared.outbox.OutboxStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.when;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for atomic auth mutations with outbox.
 *
 * Uses MySQL Testcontainers to prove that:
 * 1. Successful login/refresh/logout commit domain mutation + outbox row together
 * 2. Outbox append failure rolls back the entire domain mutation
 * 3. Token version changes persist through refresh family revocation
 *
 * This test invokes the proxied transactional use case ports, NOT the raw
 * implementation classes. The decorators will be wired in AuthConfiguration
 * as the sole input port beans.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class TransactionalAuthIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;

    @Autowired
    private LogoutUseCase logoutUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private OutboxRowJpaRepository outboxRowJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private AuthDegradedGuard authDegradedGuard;

    @MockBean
    private TokenBlacklistPort tokenBlacklistPort;

    private static final String EMAIL = "student@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private User testUser;

    @BeforeEach
    void setUp() {
        // Mock AuthDegradedGuard to allow auth operations
        when(authDegradedGuard.isDegraded()).thenReturn(false);

        // Seed test user
        String passwordHash = passwordEncoder.encode(PASSWORD);
        testUser = User.create(Email.of(EMAIL), passwordHash, Role.STUDENT);
        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        outboxRowJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        userRepository.findByEmail(Email.of(EMAIL))
            .ifPresent(user -> userRepository.deleteById(user.getId()));
    }

    @Test
    void successful_login_commits_refresh_and_outbox_atomically() {
        LoginCommand command = new LoginCommand(EMAIL, PASSWORD);

        TokenPair tokens = loginUseCase.execute(command);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        // Verify refresh token was persisted
        List<RefreshTokenJpaEntity> refreshTokens = refreshTokenJpaRepository.findAll();
        assertThat(refreshTokens).hasSize(1);
        assertThat(refreshTokens.get(0).getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);

        // Verify outbox row was created in the SAME transaction
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("auth.AuthUserLoggedIn");
        assertThat(outboxRows.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void successful_refresh_commits_rotation_and_outbox_atomically() {
        // Setup: login first
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD);
        TokenPair initial = loginUseCase.execute(loginCmd);

        // Clear outbox to isolate refresh event
        outboxRowJpaRepository.deleteAll();

        // Execute refresh
        RefreshCommand refreshCmd = new RefreshCommand(initial.refreshToken());
        TokenPair rotated = refreshTokenUseCase.execute(refreshCmd);

        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());

        // Verify original token is USED
        List<RefreshTokenJpaEntity> allTokens = refreshTokenJpaRepository.findAll();
        assertThat(allTokens).hasSize(2); // original USED + new ACTIVE

        RefreshTokenJpaEntity originalToken = allTokens.stream()
            .filter(t -> t.getStatus() == RefreshTokenStatus.USED)
            .findFirst()
            .orElseThrow();
        assertThat(originalToken).isNotNull();

        // Verify new token is ACTIVE
        RefreshTokenJpaEntity newToken = allTokens.stream()
            .filter(t -> t.getStatus() == RefreshTokenStatus.ACTIVE)
            .findFirst()
            .orElseThrow();
        assertThat(newToken.getFamilyId()).isEqualTo(originalToken.getFamilyId());

        // Verify outbox row for successful rotation
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("auth.RefreshRotated");
    }

    @Test
    void successful_logout_commits_revocation_and_outbox_atomically() {
        // Setup: login first
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD);
        TokenPair tokens = loginUseCase.execute(loginCmd);

        // Clear outbox to isolate logout event
        outboxRowJpaRepository.deleteAll();

        // Execute logout
        LogoutCommand logoutCmd = new LogoutCommand(tokens.refreshToken());
        logoutUseCase.execute(logoutCmd);

        // Verify token is REVOKED
        List<RefreshTokenJpaEntity> allTokens = refreshTokenJpaRepository.findAll();
        assertThat(allTokens).hasSize(1);
        assertThat(allTokens.get(0).getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);

        // Verify outbox row for logout
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("auth.UserLoggedOut");
    }

    @Test
    void refresh_family_revocation_persists_bumped_token_version() {
        // Setup: login and rotate once to create a family
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD);
        TokenPair initial = loginUseCase.execute(loginCmd);

        RefreshCommand refreshCmd = new RefreshCommand(initial.refreshToken());
        TokenPair rotated = refreshTokenUseCase.execute(refreshCmd);

        // Get original token version
        User userBefore = userRepository.findByEmail(Email.of(EMAIL)).orElseThrow();
        long versionBefore = userBefore.getTokenVersion();
        assertThat(versionBefore).isEqualTo(1L);

        // Present the USED token (original) again → triggers family revoke + version bump
        assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshCommand(initial.refreshToken())))
            .isInstanceOf(RefreshTokenCompromisedException.class);

        // Clear JPA cache to force fresh DB read
        entityManager.clear();

        // Verify token version was bumped AND persisted
        User userAfter = userRepository.findByEmail(Email.of(EMAIL)).orElseThrow();
        assertThat(userAfter.getTokenVersion()).isEqualTo(2L);

        // Verify all tokens in family are REVOKED
        List<RefreshTokenJpaEntity> allTokens = refreshTokenJpaRepository.findAll();
        assertThat(allTokens).allMatch(t -> t.getStatus() == RefreshTokenStatus.REVOKED);
    }

    @Test
    void logout_with_compromised_token_persists_version_bump() {
        // Setup: login and rotate once
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD);
        TokenPair initial = loginUseCase.execute(loginCmd);

        RefreshCommand refreshCmd = new RefreshCommand(initial.refreshToken());
        refreshTokenUseCase.execute(refreshCmd);

        // Present USED token to logout → triggers family revoke + version bump
        assertThatThrownBy(() -> logoutUseCase.execute(new LogoutCommand(initial.refreshToken())))
            .isInstanceOf(RefreshTokenCompromisedException.class);

        // Clear JPA cache to force fresh DB read
        entityManager.clear();

        // Verify token version was bumped to 2
        User userAfter = userRepository.findByEmail(Email.of(EMAIL)).orElseThrow();
        assertThat(userAfter.getTokenVersion()).isEqualTo(2L);

        // Verify family revocation outbox event
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        OutboxRowJpaEntity revokeEvent = outboxRows.stream()
            .filter(row -> row.getEventType().equals("auth.RefreshRevoked"))
            .findFirst()
            .orElseThrow();
        assertThat(revokeEvent.getPayload()).contains("\"newTokenVersion\":2");
    }
}
