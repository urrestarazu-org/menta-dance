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

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.DeviceAlreadyRevokedException;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RevokePhysicalDeviceUseCaseImplTest {

    private final PhysicalDeviceRepository deviceRepository = mock(PhysicalDeviceRepository.class);
    private final PhysicalDeviceAuditRepository auditRepository = mock(PhysicalDeviceAuditRepository.class);
    private final Clock clock = mock(Clock.class);

    private final RevokePhysicalDeviceUseCaseImpl useCase =
        new RevokePhysicalDeviceUseCaseImpl(deviceRepository, auditRepository, clock);

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final String HASH = "a".repeat(64);

    private PhysicalDevice activeDevice(DeviceId deviceId) {
        return new PhysicalDevice(
            deviceId, "Puerta 1", "Salon 1", HASH, DeviceStatus.ACTIVE, null,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @Test
    void active_device_transitions_to_revoked_with_one_audit_row() {
        DeviceId deviceId = DeviceId.generate();
        UUID actorId = UUID.randomUUID();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(activeDevice(deviceId)));
        when(clock.now()).thenReturn(NOW);
        when(deviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalDeviceView view = useCase.revoke(deviceId.getValue().toString(), actorId);

        assertThat(view.status()).isEqualTo(DeviceStatus.REVOKED);
        ArgumentCaptor<String> previousValueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).append(
            eq(deviceId), eq(actorId), eq("DEVICE_REVOKED"),
            previousValueCaptor.capture(), newValueCaptor.capture()
        );
        verifyNoMoreInteractions(auditRepository);
        assertThat(previousValueCaptor.getValue()).contains("status=ACTIVE");
        assertThat(newValueCaptor.getValue()).contains("status=REVOKED");
    }

    @Test
    void unknown_id_throws_device_not_found() {
        DeviceId deviceId = DeviceId.generate();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.revoke(deviceId.getValue().toString(), UUID.randomUUID()))
            .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void already_revoked_device_throws_device_already_revoked_and_appends_no_audit_row() {
        DeviceId deviceId = DeviceId.generate();
        PhysicalDevice revoked = activeDevice(deviceId).revoke(NOW);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> useCase.revoke(deviceId.getValue().toString(), UUID.randomUUID()))
            .isInstanceOf(DeviceAlreadyRevokedException.class);

        verify(deviceRepository, never()).save(any());
        verifyNoMoreInteractions(auditRepository);
    }
}
