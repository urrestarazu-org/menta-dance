package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceAuditJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceJpaEntity;
import com.menta.physical.infrastructure.persistence.mapper.PhysicalDeviceJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceAuditJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 * #44, US-PHYSICAL-007, design C4/C8 -- real MySQL 8.0, mirrors {@code
 * VirtualCourseAuditRepositoryAdapterTest}: append-only semantics and the {@code
 * fk_physical_device_audit_device} foreign key need real database semantics, not Mockito.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PhysicalDeviceAuditRepositoryAdapterTest.JpaConfiguration.class)
@Testcontainers
class PhysicalDeviceAuditRepositoryAdapterTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_physical_device_audit_test")
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
    @Autowired private PhysicalDeviceAuditJpaRepository auditRepository;

    private PhysicalDeviceAuditRepositoryAdapter adapter() {
        return new PhysicalDeviceAuditRepositoryAdapter(auditRepository);
    }

    private UUID seedDevice() {
        PhysicalDevice device = new PhysicalDevice(
            DeviceId.generate(), "Puerta 1", "Salon 1", "a".repeat(64), DeviceStatus.ACTIVE, null,
            Instant.now(), Instant.now()
        );
        deviceRepository.saveAndFlush(PhysicalDeviceJpaMapper.toEntity(device));
        return device.getId().getValue();
    }

    @Test
    void append_persists_one_row_with_the_exact_given_fields() {
        UUID deviceId = seedDevice();
        UUID actorId = UUID.randomUUID();

        adapter().append(deviceId, actorId, "DEVICE_REGISTERED", null, "status=ACTIVE;secretHashPrefix=aaaaaaaa");

        List<PhysicalDeviceAuditJpaEntity> rows = auditRepository.findByDeviceIdOrderByCreatedAtAsc(deviceId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDeviceId()).isEqualTo(deviceId);
        assertThat(rows.get(0).getActorId()).isEqualTo(actorId);
        assertThat(rows.get(0).getAction()).isEqualTo("DEVICE_REGISTERED");
        assertThat(rows.get(0).getPreviousValue()).isNull();
        assertThat(rows.get(0).getNewValue()).isEqualTo("status=ACTIVE;secretHashPrefix=aaaaaaaa");
    }

    @Test
    void rows_are_append_only_and_survive_a_subsequent_device_update() {
        UUID deviceId = seedDevice();
        UUID actorId = UUID.randomUUID();
        adapter().append(deviceId, actorId, "DEVICE_REGISTERED", null, "status=ACTIVE;secretHashPrefix=aaaaaaaa");

        PhysicalDeviceJpaEntity current = deviceRepository.findById(deviceId).orElseThrow();
        deviceRepository.saveAndFlush(new PhysicalDeviceJpaEntity(
            current.getId(), current.getName(), current.getLocation(), "b".repeat(64), "REVOKED",
            current.getExpiresAt(), current.getCreatedAt(), Instant.now()
        ));
        adapter().append(deviceId, actorId, "DEVICE_REVOKED", "status=ACTIVE;secretHashPrefix=aaaaaaaa",
            "status=REVOKED;secretHashPrefix=aaaaaaaa");

        List<PhysicalDeviceAuditJpaEntity> rows = auditRepository.findByDeviceIdOrderByCreatedAtAsc(deviceId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getAction()).isEqualTo("DEVICE_REGISTERED");
        assertThat(rows.get(1).getAction()).isEqualTo("DEVICE_REVOKED");
    }
}
