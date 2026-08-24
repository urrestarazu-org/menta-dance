package com.menta.physical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.application.port.out.AttendanceRepository;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.BatchCreatePhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalSessionUseCaseImpl;
import com.menta.physical.application.usecase.IssuePhysicalAccessQrUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalCoursesUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import com.menta.physical.application.usecase.ProcessPhysicalCheckInUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalSessionUseCaseImpl;
import com.menta.physical.infrastructure.qr.QrProperties;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;

class PhysicalConfigurationTest {

    private final Environment environment = mock(Environment.class);
    private final PhysicalConfiguration configuration = new PhysicalConfiguration(environment);

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
            mock(AttendanceRepository.class), redisTemplate, mock(Clock.class), new QrProperties()
        );

        assertThat(useCase).isInstanceOf(ProcessPhysicalCheckInUseCaseImpl.class);
    }

    @Test
    void validateDeviceTokenNotDefaultInProduction_passes_outside_production_profiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});

        configuration.validateDeviceTokenNotDefaultInProduction();
    }

    @Test
    void validateDeviceTokenNotDefaultInProduction_passes_in_production_with_a_custom_token()
        throws NoSuchFieldException, IllegalAccessException {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"staging"});
        setCheckInDeviceToken(configuration, "a-real-production-device-token");

        configuration.validateDeviceTokenNotDefaultInProduction();
    }

    @Test
    void validateDeviceTokenNotDefaultInProduction_rejects_the_dev_default_token_in_production()
        throws NoSuchFieldException, IllegalAccessException {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        setCheckInDeviceToken(configuration, devDefaultDeviceToken());

        assertThatThrownBy(configuration::validateDeviceTokenNotDefaultInProduction)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SECURITY");
    }

    private static String devDefaultDeviceToken()
        throws NoSuchFieldException, IllegalAccessException {
        Field field = PhysicalConfiguration.class.getDeclaredField("DEV_DEFAULT_DEVICE_TOKEN");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static void setCheckInDeviceToken(PhysicalConfiguration configuration, String token)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = PhysicalConfiguration.class.getDeclaredField("checkInDeviceToken");
        field.setAccessible(true);
        field.set(configuration, token);
    }
}
