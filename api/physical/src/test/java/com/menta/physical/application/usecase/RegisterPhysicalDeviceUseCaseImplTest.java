package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceSecretGenerator;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegisterPhysicalDeviceUseCaseImplTest {

    private final PhysicalDeviceRepository deviceRepository = mock(PhysicalDeviceRepository.class);
    private final PhysicalDeviceAuditRepository auditRepository = mock(PhysicalDeviceAuditRepository.class);
    private final DeviceSecretGenerator secretGenerator = mock(DeviceSecretGenerator.class);
    private final DeviceSecretHasher secretHasher = mock(DeviceSecretHasher.class);
    private final Clock clock = mock(Clock.class);

    private final RegisterPhysicalDeviceUseCaseImpl useCase = new RegisterPhysicalDeviceUseCaseImpl(
        deviceRepository, auditRepository, secretGenerator, secretHasher, clock
    );

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final String RAW_SECRET = "raw-secret-value";
    private static final String HASH = "b".repeat(64);

    private void stubHappyPath() {
        when(clock.now()).thenReturn(NOW);
        when(secretGenerator.generate()).thenReturn(RAW_SECRET);
        when(secretHasher.hash(RAW_SECRET)).thenReturn(HASH);
        when(deviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hashes_before_persisting_and_never_persists_the_raw_value() {
        stubHappyPath();

        useCase.register(new RegisterPhysicalDeviceCommand("Puerta 1", "Salon 1", null), UUID.randomUUID());

        ArgumentCaptor<PhysicalDevice> captor = ArgumentCaptor.forClass(PhysicalDevice.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getSecretHash()).isEqualTo(HASH);
        assertThat(captor.getValue().getSecretHash()).doesNotContain(RAW_SECRET);
    }

    @Test
    void returned_raw_secret_equals_the_generators_output() {
        stubHappyPath();

        PhysicalDeviceSecretResult result =
            useCase.register(new RegisterPhysicalDeviceCommand("Puerta 1", "Salon 1", null), UUID.randomUUID());

        assertThat(result.rawSecret()).isEqualTo(RAW_SECRET);
        assertThat(result.device().status()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    void appends_exactly_one_audit_row_with_device_registered_and_a_null_previous_value() {
        stubHappyPath();
        UUID actorId = UUID.randomUUID();

        useCase.register(new RegisterPhysicalDeviceCommand("Puerta 1", "Salon 1", null), actorId);

        ArgumentCaptor<DeviceId> deviceIdCaptor = ArgumentCaptor.forClass(DeviceId.class);
        ArgumentCaptor<String> previousValueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).append(
            deviceIdCaptor.capture(), org.mockito.ArgumentMatchers.eq(actorId),
            org.mockito.ArgumentMatchers.eq("DEVICE_REGISTERED"), previousValueCaptor.capture(),
            newValueCaptor.capture()
        );
        verifyNoMoreInteractions(auditRepository);

        assertThat(previousValueCaptor.getValue()).isNull();
        assertThat(newValueCaptor.getValue()).contains("status=ACTIVE").contains("secretHashPrefix=" + HASH.substring(0, 8));
    }

    @Test
    void leak_guard_audit_row_never_contains_the_raw_secret_or_a_full_hash() {
        stubHappyPath();

        useCase.register(new RegisterPhysicalDeviceCommand("Puerta 1", "Salon 1", null), UUID.randomUUID());

        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).append(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("DEVICE_REGISTERED"), org.mockito.ArgumentMatchers.isNull(),
            newValueCaptor.capture()
        );
        String newValue = newValueCaptor.getValue();
        assertThat(newValue).doesNotContain(RAW_SECRET);
        assertThat(newValue).doesNotContainPattern("[0-9a-f]{64}");
    }

    @Test
    void uses_clock_now_for_the_created_and_updated_timestamps() {
        stubHappyPath();

        PhysicalDeviceSecretResult result =
            useCase.register(new RegisterPhysicalDeviceCommand("Puerta 1", "Salon 1", null), UUID.randomUUID());

        assertThat(result.device().createdAt()).isEqualTo(NOW);
        assertThat(result.device().updatedAt()).isEqualTo(NOW);
    }
}
