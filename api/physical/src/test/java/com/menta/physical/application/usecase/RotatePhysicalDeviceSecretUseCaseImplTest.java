package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceSecretGenerator;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.exception.DeviceRevokedException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RotatePhysicalDeviceSecretUseCaseImplTest {

    private final PhysicalDeviceRepository deviceRepository = mock(PhysicalDeviceRepository.class);
    private final PhysicalDeviceAuditRepository auditRepository = mock(PhysicalDeviceAuditRepository.class);
    private final DeviceSecretGenerator secretGenerator = mock(DeviceSecretGenerator.class);
    private final DeviceSecretHasher secretHasher = mock(DeviceSecretHasher.class);
    private final Clock clock = mock(Clock.class);

    private final RotatePhysicalDeviceSecretUseCaseImpl useCase = new RotatePhysicalDeviceSecretUseCaseImpl(
        deviceRepository, auditRepository, secretGenerator, secretHasher, clock
    );

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final String OLD_HASH = "a".repeat(64);
    private static final String NEW_HASH = "b".repeat(64);

    private PhysicalDevice activeDevice(DeviceId deviceId) {
        return new PhysicalDevice(
            deviceId, "Puerta 1", "Salon 1", OLD_HASH, DeviceStatus.ACTIVE, null,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    void replaces_the_stored_hash_with_a_different_value_on_an_active_device() {
        DeviceId deviceId = DeviceId.generate();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(activeDevice(deviceId)));
        when(clock.now()).thenReturn(NOW);
        when(secretGenerator.generate()).thenReturn("new-raw-secret");
        when(secretHasher.hash("new-raw-secret")).thenReturn(NEW_HASH);
        when(deviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalDeviceSecretResult result = useCase.rotate(deviceId.getValue().toString(), UUID.randomUUID());

        ArgumentCaptor<PhysicalDevice> captor = ArgumentCaptor.forClass(PhysicalDevice.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getSecretHash()).isEqualTo(NEW_HASH).isNotEqualTo(OLD_HASH);
        assertThat(result.rawSecret()).isEqualTo("new-raw-secret");
    }

    @Test
    void appends_one_audit_row_with_old_and_new_secret_hash_prefixes() {
        DeviceId deviceId = DeviceId.generate();
        UUID actorId = UUID.randomUUID();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(activeDevice(deviceId)));
        when(clock.now()).thenReturn(NOW);
        when(secretGenerator.generate()).thenReturn("new-raw-secret");
        when(secretHasher.hash("new-raw-secret")).thenReturn(NEW_HASH);
        when(deviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.rotate(deviceId.getValue().toString(), actorId);

        ArgumentCaptor<String> previousValueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).append(
            eq(deviceId), eq(actorId), eq("DEVICE_SECRET_ROTATED"),
            previousValueCaptor.capture(), newValueCaptor.capture()
        );
        verifyNoMoreInteractions(auditRepository);

        assertThat(previousValueCaptor.getValue()).contains("secretHashPrefix=" + OLD_HASH.substring(0, 8));
        assertThat(newValueCaptor.getValue()).contains("secretHashPrefix=" + NEW_HASH.substring(0, 8));
        assertThat(previousValueCaptor.getValue()).isNotEqualTo(newValueCaptor.getValue());
    }

    @Test
    void leak_guard_audit_row_never_contains_the_raw_secret_or_a_full_hash() {
        DeviceId deviceId = DeviceId.generate();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(activeDevice(deviceId)));
        when(clock.now()).thenReturn(NOW);
        when(secretGenerator.generate()).thenReturn("new-raw-secret");
        when(secretHasher.hash("new-raw-secret")).thenReturn(NEW_HASH);
        when(deviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.rotate(deviceId.getValue().toString(), UUID.randomUUID());

        ArgumentCaptor<String> previousValueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).append(
            any(), any(), eq("DEVICE_SECRET_ROTATED"), previousValueCaptor.capture(), newValueCaptor.capture()
        );
        assertThat(previousValueCaptor.getValue()).doesNotContain("new-raw-secret").doesNotContainPattern("[0-9a-f]{64}");
        assertThat(newValueCaptor.getValue()).doesNotContain("new-raw-secret").doesNotContainPattern("[0-9a-f]{64}");
    }

    @Test
    void unknown_id_throws_device_not_found() {
        DeviceId deviceId = DeviceId.generate();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.rotate(deviceId.getValue().toString(), UUID.randomUUID()))
            .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void revoked_device_throws_device_revoked_and_changes_nothing() {
        DeviceId deviceId = DeviceId.generate();
        PhysicalDevice revoked = activeDevice(deviceId).revoke(NOW);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> useCase.rotate(deviceId.getValue().toString(), UUID.randomUUID()))
            .isInstanceOf(DeviceRevokedException.class);

        verify(deviceRepository, never()).save(any());
        verifyNoMoreInteractions(auditRepository);
    }
}
