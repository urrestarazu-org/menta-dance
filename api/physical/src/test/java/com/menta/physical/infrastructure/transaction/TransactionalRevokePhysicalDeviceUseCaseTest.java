package com.menta.physical.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.RevokePhysicalDeviceUseCase;
import com.menta.physical.domain.model.DeviceStatus;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalRevokePhysicalDeviceUseCaseTest {

    @Mock private RevokePhysicalDeviceUseCase delegate;

    @Test
    void delegates_the_revoke_call() {
        String deviceId = UUID.randomUUID().toString();
        UUID actorId = UUID.randomUUID();
        PhysicalDeviceView result = new PhysicalDeviceView(
            UUID.randomUUID(), "Puerta 1", "Salon 1", DeviceStatus.REVOKED, null, Instant.now(), Instant.now()
        );
        when(delegate.revoke(deviceId, actorId)).thenReturn(result);

        TransactionalRevokePhysicalDeviceUseCase decorator = new TransactionalRevokePhysicalDeviceUseCase(delegate);
        PhysicalDeviceView actual = decorator.revoke(deviceId, actorId);

        assertThat(actual).isEqualTo(result);
        verify(delegate).revoke(deviceId, actorId);
    }

    @Test
    void marks_the_revoke_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method revokeMethod =
            TransactionalRevokePhysicalDeviceUseCase.class.getMethod("revoke", String.class, UUID.class);

        assertThat(revokeMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
