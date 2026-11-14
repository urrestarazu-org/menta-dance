package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CreateSessionCommand;
import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.ManagedSession;
import com.menta.bff.application.dto.SessionChanges;
import com.menta.bff.application.dto.SessionCreation;
import com.menta.bff.application.dto.UpdateSessionCommand;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Session management use cases")
class ManagedSessionUseCasesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_ID = "1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    /** Tuesday 2026-11-17 22:00 in Buenos Aires. */
    private static final ManagedSession SESSION = new ManagedSession(SESSION_ID, COURSE_ID,
            Instant.parse("2026-11-18T01:00:00Z"), 15, 10, 2, 3, "SCHEDULED", "Traer zapatos");
    private static final ManagedCourse COURSE = new ManagedCourse(COURSE_ID, "Salsa", "d",
            "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "Ada", DayOfWeek.WEDNESDAY, LocalTime.of(1, 0), 60, "BEGINNER", 15,
            "ACTIVE");

    @Mock
    private PhysicalCourseManagementApiClient client;

    private ListCourseSessionsUseCase list;
    private CreateCourseSessionUseCase create;
    private BatchCreateCourseSessionsUseCase batch;
    private UpdateCourseSessionUseCase update;

    @BeforeEach
    void setUp() {
        list = new ListCourseSessionsUseCaseImpl(client, ZONE);
        create = new CreateCourseSessionUseCaseImpl(client, ZONE);
        batch = new BatchCreateCourseSessionsUseCaseImpl(client, ZONE);
        update = new UpdateCourseSessionUseCaseImpl(client, ZONE);
    }

    private static final ManagedSessionView SESSION_VIEW = new ManagedSessionView(SESSION_ID, COURSE_ID,
            Instant.parse("2026-11-18T01:00:00Z"), LocalDate.of(2026, 11, 17), LocalTime.of(22, 0),
            "martes 17 de noviembre a las 22:00", false, 15, 12, 3, "Traer zapatos");

    @Test
    @DisplayName("listing asks for the UTC instants of the local days, and words each session in the academy's zone")
    void listsTheLocalRange() {
        when(client.listCourseSessions(any(), any(), any(), any())).thenReturn(List.of(SESSION));

        List<ManagedSessionView> sessions = list.execute(
                TOKEN, COURSE_ID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(sessions).containsExactly(SESSION_VIEW);
        verify(client).listCourseSessions(TOKEN, COURSE_ID,
                Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-12-01T03:00:00Z"));
    }

    @Test
    @DisplayName("listing without a range leaves it to the API, and a cancelled session reads as cancelled")
    void listsWithoutRange() {
        ManagedSession cancelled = new ManagedSession(SESSION_ID, COURSE_ID, Instant.parse("2026-11-18T01:00:00Z"),
                15, 0, 0, 15, "CANCELLED", null);
        when(client.listCourseSessions(TOKEN, COURSE_ID, null, null)).thenReturn(List.of(cancelled));

        assertThat(list.execute(TOKEN, COURSE_ID, null, null)).singleElement().satisfies(view -> {
            assertThat(view.cancelled()).isTrue();
            assertThat(view.occupied()).isZero();
            assertThat(view.notes()).isNull();
        });
    }

    @Test
    @DisplayName("a session that starts at the very end of the range, if the API returns it, is left out")
    void dropsTheEndOfTheRange() {
        ManagedSession atTheEnd = new ManagedSession("2b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", COURSE_ID,
                Instant.parse("2026-12-01T03:00:00Z"), 15, 0, 0, 15, "SCHEDULED", null);
        when(client.listCourseSessions(any(), any(), any(), any())).thenReturn(List.of(SESSION, atTheEnd));

        assertThat(list.execute(TOKEN, COURSE_ID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .containsExactly(SESSION_VIEW);
    }

    @Test
    @DisplayName("creating a session converts the local date and hour to UTC, crossing midnight")
    void createsInUtc() {
        when(client.createSession(any(), any(), any())).thenReturn(SESSION);

        ManagedSessionView created = create.execute(TOKEN, COURSE_ID.toUpperCase(),
                new CreateSessionCommand(LocalDate.of(2026, 11, 17), LocalTime.of(22, 0), null, "Traer zapatos"));

        assertThat(created).isEqualTo(SESSION_VIEW);
        verify(client).createSession(TOKEN, COURSE_ID,
                new SessionCreation(LocalDate.of(2026, 11, 18), LocalTime.of(1, 0), null, "Traer zapatos"));
    }

    @Test
    @DisplayName("a batch converts the local range with the course's local time, so the same sessions are generated")
    void batchConvertsTheRange() {
        when(client.listCourses(TOKEN)).thenReturn(List.of(COURSE));
        when(client.createSessionsInBatch(any(), any(), any(), any())).thenReturn(List.of(SESSION));

        List<ManagedSessionView> created = batch.execute(
                TOKEN, COURSE_ID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(created).containsExactly(SESSION_VIEW);
        verify(client).createSessionsInBatch(TOKEN, COURSE_ID, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("a batch for a course the caller does not see reads as COURSE_NOT_FOUND, and creates nothing")
    void batchOfUnknownCourse() {
        when(client.listCourses(TOKEN)).thenReturn(List.of());

        assertThatThrownBy(() -> batch.execute(TOKEN, COURSE_ID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
        verify(client).listCourses(TOKEN);
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("rescheduling converts the local date and hour to UTC")
    void reschedulesInUtc() {
        when(client.updateSession(any(), any(), any())).thenReturn(SESSION);

        update.execute(TOKEN, SESSION_ID, new UpdateSessionCommand(
                LocalDate.of(2026, 11, 24), LocalTime.of(23, 30), null, null, null));

        verify(client).updateSession(TOKEN, SESSION_ID,
                new SessionChanges(LocalDate.of(2026, 11, 25), LocalTime.of(2, 30), null, null, null));
    }

    @Test
    @DisplayName("cancelling sends only the status")
    void cancels() {
        when(client.updateSession(any(), any(), any())).thenReturn(SESSION);

        update.execute(TOKEN, SESSION_ID, new UpdateSessionCommand(null, null, null, null, "CANCELLED"));

        verify(client).updateSession(TOKEN, SESSION_ID, new SessionChanges(null, null, null, null, "CANCELLED"));
    }

    @Test
    @DisplayName("the date and the time of a reschedule travel together: one without the other is refused")
    void rescheduleIsAPair() {
        assertThatThrownBy(() -> new UpdateSessionCommand(LocalDate.MAX, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateSessionCommand(null, LocalTime.NOON, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an id that is not a UUID never reaches the API: a course or a session that does not exist")
    void invalidIds() {
        assertThatThrownBy(() -> list.execute(TOKEN, "x/../y", null, null))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
        assertThatThrownBy(() -> create.execute(TOKEN, "x",
                new CreateSessionCommand(LocalDate.MAX, LocalTime.NOON, null, null)))
                .isInstanceOf(CourseManagementException.class);
        assertThatThrownBy(() -> batch.execute(TOKEN, "x", LocalDate.MAX, LocalDate.MAX))
                .isInstanceOf(CourseManagementException.class);
        assertThatThrownBy(() -> update.execute(TOKEN, "x", new UpdateSessionCommand(null, null, null, null, null)))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.SESSION_NOT_FOUND));
        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("without an access token nothing is sent: it reads as unavailable")
    void withoutToken(String token) {
        LocalDate date = LocalDate.of(2026, 11, 17);
        assertThatThrownBy(() -> list.execute(token, COURSE_ID, null, null))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> create.execute(token, COURSE_ID,
                new CreateSessionCommand(date, LocalTime.NOON, null, null)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> batch.execute(token, COURSE_ID, date, date))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> update.execute(token, SESSION_ID,
                new UpdateSessionCommand(null, null, null, null, "CANCELLED")))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a conflict of the API reaches the caller with its dates, in UTC")
    void conflictPropagates() {
        when(client.createSession(any(), any(), any())).thenThrow(new CourseManagementException(
                Code.SESSION_ALREADY_EXISTS, List.of(Instant.parse("2026-11-18T01:00:00Z"))));

        assertThatThrownBy(() -> create.execute(TOKEN, COURSE_ID,
                new CreateSessionCommand(LocalDate.of(2026, 11, 17), LocalTime.of(22, 0), null, null)))
                .isInstanceOfSatisfying(CourseManagementException.class, e -> assertThat(e.getConflictingDates())
                        .containsExactly(Instant.parse("2026-11-18T01:00:00Z")));
    }
}
