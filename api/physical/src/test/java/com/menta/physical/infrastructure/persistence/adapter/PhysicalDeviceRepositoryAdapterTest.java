package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceJpaRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * #44, US-PHYSICAL-007, design C3/C8 -- real MySQL 8.0, mirrors {@code
 * PhysicalCapacityAssignmentRepositoryAdapterTest}: the {@code uq_physical_devices_secret_hash}
 * unique-constraint round trip needs real database semantics, not Mockito.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PhysicalDeviceRepositoryAdapterTest.JpaConfiguration.class)
@Testcontainers
class PhysicalDeviceRepositoryAdapterTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_physical_device_test")
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
    @EntityScan(basePackageClasses = PhysicalDeviceJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = PhysicalDeviceJpaRepository.class)
    static class JpaConfiguration {
    }

    @Autowired private PhysicalDeviceJpaRepository deviceRepository;

    private PhysicalDeviceRepositoryAdapter adapter() {
        return new PhysicalDeviceRepositoryAdapter(deviceRepository);
    }

    private static PhysicalDevice device(String name, String secretHash, Instant expiresAt, Instant createdAt) {
        return new PhysicalDevice(
            DeviceId.generate(), name, "Salon 1", secretHash, DeviceStatus.ACTIVE, expiresAt, createdAt, createdAt
        );
    }

    @Test
    void round_trips_all_fields_including_a_null_expires_at() {
        PhysicalDevice device = device("Puerta 1", "a".repeat(64), null, Instant.parse("2026-09-25T10:00:00Z"));

        PhysicalDevice saved = adapter().save(device);
        PhysicalDevice found = adapter().findById(device.getId()).orElseThrow();

        assertThat(saved.getId()).isEqualTo(device.getId());
        assertThat(found.getName()).isEqualTo("Puerta 1");
        assertThat(found.getLocation()).isEqualTo("Salon 1");
        assertThat(found.getSecretHash()).isEqualTo("a".repeat(64));
        assertThat(found.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(found.getExpiresAt()).isNull();
        assertThat(found.getCreatedAt()).isEqualTo(Instant.parse("2026-09-25T10:00:00Z"));
    }

    @Test
    void round_trips_a_non_null_expires_at() {
        Instant expiresAt = Instant.parse("2027-01-01T00:00:00Z");
        PhysicalDevice device = device("Puerta 2", "b".repeat(64), expiresAt, Instant.now());

        adapter().save(device);
        PhysicalDevice found = adapter().findById(device.getId()).orElseThrow();

        assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void find_by_id_returns_empty_when_absent() {
        assertThat(adapter().findById(DeviceId.generate())).isEmpty();
    }

    @Test
    void unique_secret_hash_rejects_a_duplicate() {
        String sharedHash = "c".repeat(64);
        adapter().save(device("Puerta 3", sharedHash, null, Instant.now()));

        assertThatThrownBy(() -> {
            deviceRepository.saveAndFlush(com.menta.physical.infrastructure.persistence.mapper
                .PhysicalDeviceJpaMapper.toEntity(device("Puerta 4", sharedHash, null, Instant.now())));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void find_all_orders_by_created_at_then_id_ascending() {
        PhysicalDevice earlier = device("Earlier", "d".repeat(64), null, Instant.parse("2026-09-01T00:00:00Z"));
        PhysicalDevice later = device("Later", "e".repeat(64), null, Instant.parse("2026-09-10T00:00:00Z"));
        adapter().save(later);
        adapter().save(earlier);

        var all = adapter().findAll();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getId()).isEqualTo(earlier.getId());
        assertThat(all.get(1).getId()).isEqualTo(later.getId());
    }
}
