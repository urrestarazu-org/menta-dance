package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.menta.testsupport.MySqlTestImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The listing query and the row locks need real MySQL semantics (LIKE escaping, collation, {@code FOR UPDATE}),
 * so this runs on Testcontainers like the other repository tests that depend on the engine.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = UserAdminRepositoryAdapterTest.JpaConfiguration.class)
@Testcontainers
class UserAdminRepositoryAdapterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);
    private static final String PHONE = "+5491100000000";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
        .withDatabaseName("menta_user_admin_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EntityScan(basePackageClasses = UserJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = UserJpaRepository.class)
    static class JpaConfiguration {
    }

    @Autowired private UserJpaRepository jpaRepository;

    private UserAdminRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
        adapter = new UserAdminRepositoryAdapter(jpaRepository);
    }

    private UserJpaEntity seed(String email, Role role, UserStatus status, String firstName, String lastName) {
        UserJpaEntity entity = new UserJpaEntity(
            UUID.randomUUID(), email, "hash", role, status, NOW, NOW, 1L,
            firstName, lastName, firstName == null ? null : PHONE, null
        );
        return jpaRepository.saveAndFlush(entity);
    }

    private UserJpaEntity seed(String email, Role role) {
        return seed(email, role, UserStatus.ACTIVE, "Nombre", "Apellido");
    }

    private UserPage list(Role role, String search, int page, int size) {
        return adapter.search(new ListUsersQuery(role, search, page, size));
    }

    private static java.util.List<String> emails(UserPage page) {
        return page.items().stream().map(UserSummary::email).toList();
    }

    @Test
    void lists_users_ordered_by_email() {
        seed("carla@example.com", Role.STUDENT);
        seed("ana@example.com", Role.ADMIN);
        seed("beto@example.com", Role.INSTRUCTOR);

        UserPage page = list(null, null, 0, 20);

        assertThat(emails(page)).containsExactly("ana@example.com", "beto@example.com", "carla@example.com");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void pages_through_the_users_and_reports_totals() {
        for (String name : new String[] {"a", "b", "c", "d", "e"}) {
            seed(name + "@example.com", Role.STUDENT);
        }

        UserPage second = list(null, null, 1, 2);

        assertThat(emails(second)).containsExactly("c@example.com", "d@example.com");
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(second.totalPages()).isEqualTo(3);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
    }

    @Test
    void a_page_past_the_end_has_no_items_but_keeps_the_totals() {
        seed("a@example.com", Role.STUDENT);

        UserPage page = list(null, null, 5, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void filters_by_role() {
        seed("ana@example.com", Role.ADMIN);
        seed("beto@example.com", Role.INSTRUCTOR);
        seed("carla@example.com", Role.INSTRUCTOR);

        UserPage page = list(Role.INSTRUCTOR, null, 0, 20);

        assertThat(emails(page)).containsExactly("beto@example.com", "carla@example.com");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void searches_by_email_ignoring_case() {
        seed("ana.perez@example.com", Role.STUDENT);
        seed("beto@example.com", Role.STUDENT);

        assertThat(emails(list(null, "PEREZ", 0, 20))).containsExactly("ana.perez@example.com");
    }

    @Test
    void searches_by_first_name_and_by_last_name_ignoring_case() {
        seed("a@example.com", Role.STUDENT, UserStatus.ACTIVE, "Lucía", "Gómez");
        seed("b@example.com", Role.STUDENT, UserStatus.ACTIVE, "Mario", "Lucero");
        seed("c@example.com", Role.STUDENT, UserStatus.ACTIVE, "Pedro", "Rossi");

        assertThat(emails(list(null, "LUC", 0, 20))).containsExactly("a@example.com", "b@example.com");
        assertThat(emails(list(null, "rossi", 0, 20))).containsExactly("c@example.com");
    }

    @Test
    void combines_role_and_search() {
        seed("ana@example.com", Role.ADMIN, UserStatus.ACTIVE, "Ana", "Uno");
        seed("anibal@example.com", Role.STUDENT, UserStatus.ACTIVE, "Anibal", "Dos");

        assertThat(emails(list(Role.STUDENT, "an", 0, 20))).containsExactly("anibal@example.com");
    }

    @Test
    void a_percent_sign_in_the_search_is_literal_not_a_wildcard() {
        seed("a@example.com", Role.STUDENT, UserStatus.ACTIVE, "100% Ana", "Uno");
        seed("b@example.com", Role.STUDENT, UserStatus.ACTIVE, "Beto", "Dos");

        assertThat(emails(list(null, "%", 0, 20))).containsExactly("a@example.com");
    }

    @Test
    void an_underscore_in_the_search_is_literal_not_a_single_character_wildcard() {
        seed("a@example.com", Role.STUDENT, UserStatus.ACTIVE, "a_b", "Uno");
        seed("b@example.com", Role.STUDENT, UserStatus.ACTIVE, "axb", "Dos");

        assertThat(emails(list(null, "a_b", 0, 20))).containsExactly("a@example.com");
    }

    @Test
    void a_backslash_in_the_search_is_literal() {
        seed("a@example.com", Role.STUDENT, UserStatus.ACTIVE, "back\\slash", "Uno");
        seed("b@example.com", Role.STUDENT, UserStatus.ACTIVE, "backslash", "Dos");

        assertThat(emails(list(null, "\\", 0, 20))).containsExactly("a@example.com");
        assertThat(emails(list(null, "back\\s", 0, 20))).containsExactly("a@example.com");
    }

    @Test
    void maps_the_summary_without_credentials() {
        UserJpaEntity stored = seed("ada@example.com", Role.INSTRUCTOR, UserStatus.INACTIVE, "Ada", "Lovelace");

        UserSummary summary = list(null, null, 0, 20).items().get(0);

        assertThat(summary.id()).isEqualTo(stored.getId().toString());
        assertThat(summary.email()).isEqualTo("ada@example.com");
        assertThat(summary.firstName()).isEqualTo("Ada");
        assertThat(summary.lastName()).isEqualTo("Lovelace");
        assertThat(summary.role()).isEqualTo(Role.INSTRUCTOR);
        assertThat(summary.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(summary.demo()).isFalse();
        assertThat(summary.createdAt()).isEqualTo(NOW);
    }

    @Test
    void an_account_without_profile_has_null_names() {
        seed("admin@example.com", Role.ADMIN, UserStatus.ACTIVE, null, null);

        UserSummary summary = list(null, null, 0, 20).items().get(0);

        assertThat(summary.firstName()).isNull();
        assertThat(summary.lastName()).isNull();
    }

    @Test
    void locks_only_the_active_administrators() {
        UserJpaEntity activeAdmin = seed("a1@example.com", Role.ADMIN);
        UserJpaEntity otherActiveAdmin = seed("a2@example.com", Role.ADMIN);
        seed("a3@example.com", Role.ADMIN, UserStatus.INACTIVE, "Nombre", "Apellido");
        seed("i1@example.com", Role.INSTRUCTOR);

        Set<UserId> locked = adapter.lockActiveAdminIds();

        assertThat(locked).containsExactlyInAnyOrder(
            UserId.of(activeAdmin.getId()), UserId.of(otherActiveAdmin.getId())
        );
    }

    @Test
    void finds_a_user_for_update_when_it_exists() {
        UserJpaEntity stored = seed("a@example.com", Role.STUDENT);

        Optional<User> found = adapter.findByIdForUpdate(UserId.of(stored.getId()));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(UserId.of(stored.getId()));
        assertThat(found.get().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void finds_nothing_for_update_when_the_user_does_not_exist() {
        assertThat(adapter.findByIdForUpdate(UserId.generate())).isEmpty();
    }

    @Test
    void finds_the_summaries_of_the_requested_users_only() {
        UserJpaEntity ana = seed("ana@example.com", Role.STUDENT, UserStatus.ACTIVE, "Ana", "Perez");
        UserJpaEntity admin = seed("admin@example.com", Role.ADMIN, UserStatus.ACTIVE, null, null);
        seed("otra@example.com", Role.STUDENT);

        List<UserSummary> found = adapter.findSummariesByIds(
            List.of(UserId.of(ana.getId()), UserId.of(admin.getId()), UserId.generate())
        );

        assertThat(found).extracting(UserSummary::email)
            .containsExactlyInAnyOrder("ana@example.com", "admin@example.com");
        assertThat(found).filteredOn(summary -> summary.email().equals("ana@example.com")).singleElement()
            .satisfies(summary -> {
                assertThat(summary.id()).isEqualTo(ana.getId().toString());
                assertThat(summary.firstName()).isEqualTo("Ana");
                assertThat(summary.lastName()).isEqualTo("Perez");
            });
    }

    @Test
    void finds_no_summaries_for_users_that_do_not_exist_or_for_an_empty_request() {
        seed("ana@example.com", Role.STUDENT);

        assertThat(adapter.findSummariesByIds(List.of(UserId.generate()))).isEmpty();
        assertThat(adapter.findSummariesByIds(List.of())).isEmpty();
    }
}
