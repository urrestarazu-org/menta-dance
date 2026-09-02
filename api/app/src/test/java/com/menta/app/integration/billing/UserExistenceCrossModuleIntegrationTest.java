package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.auth.UserExistencePort;
import com.menta.shared.domain.vo.Email;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the D8 cross-module wiring (US-BILLING-012, design.md A8/A9/A10) for real: {@code
 * api:app}'s {@code @SpringBootApplication(scanBasePackages = "com.menta")} resolves {@code
 * auth}'s {@code UserExistenceAdapter} as the sole {@link UserExistencePort} bean, with no
 * {@code @Bean} anywhere.
 *
 * <p>No mock of {@link UserExistencePort} anywhere in this class — the whole point is exercising
 * the real bean, seeded through {@code auth}'s own {@link UserRepository}, against a real MySQL
 * database (Testcontainers).</p>
 *
 * <p>{@code webEnvironment = NONE}: this test proves bean wiring, not an HTTP contract — the
 * admin route that will call this port ships in Phase 3/4. Mirrors {@code
 * TransactionalAuthIntegrationTest}'s minimal Redis-mock set for a {@code NONE} context.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class UserExistenceCrossModuleIntegrationTest {

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

    @Autowired private UserExistencePort userExistencePort;
    @Autowired private UserRepository userRepository;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private LoginRateLimitPort loginRateLimitPort;

    private User seededUser;

    @BeforeEach
    void setUp() {
        when(authDegradedGuard.isDegraded()).thenReturn(false);
        when(loginRateLimitPort.check(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(RateLimitDecision.allowed());

        seededUser = User.create(
            Email.of("trial-existence-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.STUDENT
        );
        userRepository.save(seededUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(seededUser.getId());
    }

    @Test
    void resolves_the_real_auth_bean_and_confirms_an_existing_user() {
        boolean exists = userExistencePort.existsById(seededUser.getId().getValue());

        assertThat(exists).isTrue();
    }

    @Test
    void resolves_the_real_auth_bean_and_rejects_a_random_uuid() {
        boolean exists = userExistencePort.existsById(UUID.randomUUID());

        assertThat(exists).isFalse();
    }
}
