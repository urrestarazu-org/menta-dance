package com.menta.physical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.DeviceAuthenticationRejection;
import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.in.GetPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.in.ListPhysicalDevicesUseCase;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.RevokePhysicalDeviceUseCase;
import com.menta.physical.application.port.in.RotatePhysicalDeviceSecretUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.application.port.out.AttendanceRepository;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceAuthenticationRejectionPort;
import com.menta.physical.application.port.out.DeviceSecretGenerator;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.BatchCreatePhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalSessionUseCaseImpl;
import com.menta.physical.application.usecase.GetPhysicalDeviceUseCaseImpl;
import com.menta.physical.application.usecase.IssuePhysicalAccessQrUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalCoursesUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.ListPhysicalDevicesUseCaseImpl;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import com.menta.physical.application.usecase.PhysicalDeviceAuthenticator;
import com.menta.physical.application.usecase.ProcessPhysicalCheckInUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalSessionUseCaseImpl;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.infrastructure.device.SecureRandomDeviceSecretGenerator;
import com.menta.physical.infrastructure.device.Sha256DeviceSecretHasher;
import com.menta.physical.infrastructure.qr.QrProperties;
import com.menta.physical.infrastructure.transaction.TransactionalRegisterPhysicalDeviceUseCase;
import com.menta.physical.infrastructure.transaction.TransactionalRevokePhysicalDeviceUseCase;
import com.menta.physical.infrastructure.transaction.TransactionalRotatePhysicalDeviceSecretUseCase;
import com.menta.shared.auth.UserExistencePort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

class PhysicalConfigurationTest {

    private final PhysicalConfiguration configuration = new PhysicalConfiguration();

    @Test
    void wires_the_availability_port_bean_with_the_given_repositories() {
        PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
        PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);

        PhysicalCourseAvailabilityPort port =
            configuration.physicalCourseAvailabilityPort(courseRepository, sessionRepository);

        assertThat(port).isInstanceOf(PhysicalCourseAvailabilityPortImpl.class);
    }

    @Test
    void wires_the_create_course_use_case_bean() {
        CreatePhysicalCourseUseCase useCase =
            configuration.createPhysicalCourseUseCase(mock(PhysicalCourseRepository.class));

        assertThat(useCase).isInstanceOf(CreatePhysicalCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_list_managed_courses_use_case_bean() {
        ListManagedPhysicalCoursesUseCase useCase =
            configuration.listManagedPhysicalCoursesUseCase(mock(PhysicalCourseRepository.class));

        assertThat(useCase).isInstanceOf(ListManagedPhysicalCoursesUseCaseImpl.class);
    }

    @Test
    void wires_the_update_course_use_case_bean() {
        UpdatePhysicalCourseUseCase useCase = configuration.updatePhysicalCourseUseCase(
            mock(PhysicalCourseRepository.class), mock(PhysicalSessionRepository.class)
        );

        assertThat(useCase).isInstanceOf(UpdatePhysicalCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_create_session_use_case_bean() {
        CreatePhysicalSessionUseCase useCase = configuration.createPhysicalSessionUseCase(
            mock(PhysicalCourseRepository.class), mock(PhysicalSessionRepository.class)
        );

        assertThat(useCase).isInstanceOf(CreatePhysicalSessionUseCaseImpl.class);
    }

    @Test
    void wires_the_batch_create_sessions_use_case_bean() {
        BatchCreatePhysicalSessionsUseCase useCase = configuration.batchCreatePhysicalSessionsUseCase(
            mock(PhysicalCourseRepository.class), mock(PhysicalSessionRepository.class)
        );

        assertThat(useCase).isInstanceOf(BatchCreatePhysicalSessionsUseCaseImpl.class);
    }

    @Test
    void wires_the_list_managed_sessions_use_case_bean() {
        ListManagedPhysicalSessionsUseCase useCase = configuration.listManagedPhysicalSessionsUseCase(
            mock(PhysicalCourseRepository.class), mock(PhysicalSessionRepository.class)
        );

        assertThat(useCase).isInstanceOf(ListManagedPhysicalSessionsUseCaseImpl.class);
    }

    @Test
    void wires_the_update_session_use_case_bean() {
        UpdatePhysicalSessionUseCase useCase = configuration.updatePhysicalSessionUseCase(
            mock(PhysicalCourseRepository.class), mock(PhysicalSessionRepository.class)
        );

        assertThat(useCase).isInstanceOf(UpdatePhysicalSessionUseCaseImpl.class);
    }

    @Test
    void wires_the_clock_bean() {
        Clock clock = configuration.physicalClock();

        assertThat(clock).isNotNull();
        assertThat(clock.now()).isNotNull();
    }

    @Test
    void wires_the_issue_access_qr_use_case_bean() {
        IssuePhysicalAccessQrUseCase useCase = configuration.issuePhysicalAccessQrUseCase(
            mock(PhysicalSessionRepository.class), mock(PhysicalCapacityAssignmentRepository.class),
            mock(Clock.class), new QrProperties()
        );

        assertThat(useCase).isInstanceOf(IssuePhysicalAccessQrUseCaseImpl.class);
    }

    @Test
    void wires_the_process_check_in_use_case_bean() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        ProcessPhysicalCheckInUseCase useCase = configuration.processPhysicalCheckInUseCase(
            mock(PhysicalSessionRepository.class), mock(PhysicalCapacityAssignmentRepository.class),
            mock(AttendanceRepository.class), redisTemplate, mock(Clock.class), new QrProperties(),
            mock(UserExistencePort.class), mock(PhysicalDeviceAuthenticator.class)
        );

        assertThat(useCase).isInstanceOf(ProcessPhysicalCheckInUseCaseImpl.class);
    }

    @Test
    void wires_the_device_authenticator_bean_with_the_given_collaborators() {
        PhysicalDeviceRepository repository = mock(PhysicalDeviceRepository.class);
        DeviceSecretHasher hasher = mock(DeviceSecretHasher.class);
        when(hasher.hash("some-secret")).thenReturn("some-hash");
        DeviceAuthenticationRejectionPort rejectionPort =
            mock(DeviceAuthenticationRejectionPort.class);
        PhysicalDeviceAuthenticator authenticator = configuration.physicalDeviceAuthenticator(
            repository, hasher, mock(Clock.class), rejectionPort
        );

        // An unknown (but well-formed) id walks the whole chain: registry lookup, hash, report.
        String unknownId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> authenticator.authenticate(unknownId, "some-secret"))
            .isInstanceOf(InvalidDeviceTokenException.class);
        verify(repository).findById(any(DeviceId.class));
        verify(hasher).hash("some-secret");
        verify(rejectionPort).report(any(DeviceAuthenticationRejection.class));
    }

    @Test
    void wires_the_device_secret_generator_bean() {
        DeviceSecretGenerator generator = configuration.deviceSecretGenerator();

        assertThat(generator).isInstanceOf(SecureRandomDeviceSecretGenerator.class);
    }

    @Test
    void wires_the_device_secret_hasher_bean() {
        DeviceSecretHasher hasher = configuration.deviceSecretHasher();

        assertThat(hasher).isInstanceOf(Sha256DeviceSecretHasher.class);
    }

    @Test
    void wires_the_register_physical_device_use_case_bean_decorated_transactionally() {
        RegisterPhysicalDeviceUseCase useCase = configuration.registerPhysicalDeviceUseCase(
            mock(PhysicalDeviceRepository.class), mock(PhysicalDeviceAuditRepository.class),
            mock(DeviceSecretGenerator.class), mock(DeviceSecretHasher.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalRegisterPhysicalDeviceUseCase.class);
    }

    @Test
    void wires_the_get_physical_device_use_case_bean() {
        GetPhysicalDeviceUseCase useCase =
            configuration.getPhysicalDeviceUseCase(mock(PhysicalDeviceRepository.class));

        assertThat(useCase).isInstanceOf(GetPhysicalDeviceUseCaseImpl.class);
    }

    @Test
    void wires_the_rotate_physical_device_secret_use_case_bean_decorated_transactionally() {
        RotatePhysicalDeviceSecretUseCase useCase = configuration.rotatePhysicalDeviceSecretUseCase(
            mock(PhysicalDeviceRepository.class), mock(PhysicalDeviceAuditRepository.class),
            mock(DeviceSecretGenerator.class), mock(DeviceSecretHasher.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalRotatePhysicalDeviceSecretUseCase.class);
    }

    @Test
    void wires_the_revoke_physical_device_use_case_bean_decorated_transactionally() {
        RevokePhysicalDeviceUseCase useCase = configuration.revokePhysicalDeviceUseCase(
            mock(PhysicalDeviceRepository.class), mock(PhysicalDeviceAuditRepository.class), mock(Clock.class)
        );

        assertThat(useCase).isInstanceOf(TransactionalRevokePhysicalDeviceUseCase.class);
    }

    @Test
    void wires_the_list_physical_devices_use_case_bean() {
        ListPhysicalDevicesUseCase useCase =
            configuration.listPhysicalDevicesUseCase(mock(PhysicalDeviceRepository.class));

        assertThat(useCase).isInstanceOf(ListPhysicalDevicesUseCaseImpl.class);
    }
}
