package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentProofJpaRepository;
import java.time.Instant;
import java.util.Optional;
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
 * The unique-key replacement semantics (design C5 — "a second save for the same paymentId
 * overwrites the row") rest on a real database constraint that H2's create-drop schema (every
 * other {@code @DataJpaTest} in this module, e.g. {@code SubscriptionJpaRepositoryTest}) does not
 * reliably reproduce, so this test runs against real MySQL 8, mirroring api/virtual's {@code
 * LessonProgressRepositoryAdapterProjectionTest} precedent.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PaymentProofRepositoryAdapterTest.JpaConfiguration.class)
@Testcontainers
class PaymentProofRepositoryAdapterTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_billing_payment_proofs_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Replace.NONE keeps the real MySQL container instead of an embedded database, but also
        // opts out of @DataJpaTest's implicit create-drop schema generation — this module has no
        // Flyway dependency (unlike :api:app), so Hibernate must build the schema itself from the
        // scanned entity.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EntityScan(basePackageClasses = PaymentProofJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = PaymentProofJpaRepository.class)
    static class JpaConfiguration {
    }

    @Autowired private PaymentProofJpaRepository jpaRepository;
    private PaymentProofRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PaymentProofRepositoryAdapter(jpaRepository);
    }

    @Test
    void save_then_findByPaymentId_round_trips() {
        PaymentId paymentId = PaymentId.generate();
        PaymentProof proof = PaymentProof.create(
            paymentId, "image/png", "comprobante.png", 1024, Instant.parse("2026-01-01T00:00:00Z")
        );

        adapter.save(proof);

        Optional<PaymentProof> found = adapter.findByPaymentId(paymentId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(proof.getId());
        assertThat(found.get().getPaymentId()).isEqualTo(paymentId);
        assertThat(found.get().getStorageKey()).isEqualTo(proof.getStorageKey());
        assertThat(found.get().getOriginalFilename()).isEqualTo("comprobante.png");
        assertThat(found.get().getContentType()).isEqualTo("image/png");
        assertThat(found.get().getSizeBytes()).isEqualTo(1024);
        assertThat(found.get().getUploadedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void a_second_save_for_the_same_payment_overwrites_the_previous_row() {
        PaymentId paymentId = PaymentId.generate();
        PaymentProof first = PaymentProof.create(
            paymentId, "image/png", "primero.png", 100, Instant.parse("2026-01-01T00:00:00Z")
        );
        adapter.save(first);

        PaymentProof second = PaymentProof.create(
            paymentId, "application/pdf", "segundo.pdf", 200, Instant.parse("2026-01-02T00:00:00Z")
        );
        adapter.save(second);

        Optional<PaymentProof> found = adapter.findByPaymentId(paymentId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(second.getId());
        assertThat(found.get().getOriginalFilename()).isEqualTo("segundo.pdf");
        assertThat(found.get().getContentType()).isEqualTo("application/pdf");
        assertThat(jpaRepository.findAll()).hasSize(1);
    }

    @Test
    void findByPaymentId_is_empty_when_no_proof_was_ever_submitted() {
        assertThat(adapter.findByPaymentId(PaymentId.generate())).isEmpty();
    }
}
