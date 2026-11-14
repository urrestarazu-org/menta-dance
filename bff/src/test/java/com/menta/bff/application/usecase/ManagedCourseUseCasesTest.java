package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CourseChanges;
import com.menta.bff.application.dto.CourseCreation;
import com.menta.bff.application.dto.CreateCourseCommand;
import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.UpdateCourseCommand;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import java.time.DayOfWeek;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Course management use cases")
class ManagedCourseUseCasesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String PROFESSOR_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    /** Tuesday 22:00 in Buenos Aires. */
    private static final ManagedCourse COURSE = new ManagedCourse(COURSE_ID, "Salsa", "Nivel inicial", PROFESSOR_ID,
            "Ada Lovelace", DayOfWeek.WEDNESDAY, LocalTime.of(1, 0), 60, "BEGINNER", 15, "ACTIVE");

    @Mock
    private PhysicalCourseManagementApiClient client;

    private ListManagedCoursesUseCase list;
    private CreateManagedCourseUseCase create;
    private UpdateManagedCourseUseCase update;

    @BeforeEach
    void setUp() {
        list = new ListManagedCoursesUseCaseImpl(client, ZONE);
        create = new CreateManagedCourseUseCaseImpl(client, ZONE);
        update = new UpdateManagedCourseUseCaseImpl(client, ZONE);
    }

    private static CreateCourseCommand command(String professorId) {
        return new CreateCourseCommand("Salsa", "Nivel inicial", professorId, "Ada Lovelace", DayOfWeek.TUESDAY,
                LocalTime.of(22, 0), 60, "BEGINNER", 15);
    }

    @Test
    @DisplayName("listing words each course in the academy's zone, keeping the local day and time for the forms")
    void listsInTheAcademyZone() {
        when(client.listCourses(TOKEN)).thenReturn(List.of(COURSE));

        List<ManagedCourseView> courses = list.execute(TOKEN);

        assertThat(courses).containsExactly(new ManagedCourseView(COURSE_ID, "Salsa", "Nivel inicial", PROFESSOR_ID,
                "Ada Lovelace", DayOfWeek.TUESDAY, LocalTime.of(22, 0), "Martes 22:00", 60, "BEGINNER", 15, true));
    }

    @Test
    @DisplayName("an inactive course is not active in the view")
    void inactiveCourse() {
        when(client.listCourses(TOKEN)).thenReturn(List.of(new ManagedCourse(COURSE_ID, "Salsa", "d", PROFESSOR_ID,
                "Ada", DayOfWeek.MONDAY, LocalTime.NOON, 60, "ADVANCED", 10, "INACTIVE")));

        assertThat(list.execute(TOKEN)).singleElement().satisfies(course -> {
            assertThat(course.active()).isFalse();
            assertThat(course.scheduleLabel()).isEqualTo("Lunes 09:00");
        });
    }

    @Test
    @DisplayName("creating converts the local weekday and hour to UTC and always sends the professor")
    void createsInUtcWithTheProfessor() {
        when(client.createCourse(any(), any())).thenReturn(COURSE);

        ManagedCourseView created = create.execute(TOKEN, command(PROFESSOR_ID));

        ArgumentCaptor<CourseCreation> sent = ArgumentCaptor.forClass(CourseCreation.class);
        verify(client).createCourse(eq(TOKEN), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new CourseCreation("Salsa", "Nivel inicial", PROFESSOR_ID,
                "Ada Lovelace", DayOfWeek.WEDNESDAY, LocalTime.of(1, 0), 60, "BEGINNER", 15));
        assertThat(created.scheduleLabel()).isEqualTo("Martes 22:00");
    }

    @Test
    @DisplayName("the professor id is normalized before it is sent")
    void professorIsNormalized() {
        when(client.createCourse(any(), any())).thenReturn(COURSE);

        create.execute(TOKEN, command("  " + PROFESSOR_ID.toUpperCase() + " "));

        ArgumentCaptor<CourseCreation> sent = ArgumentCaptor.forClass(CourseCreation.class);
        verify(client).createCourse(eq(TOKEN), sent.capture());
        assertThat(sent.getValue().professorId()).isEqualTo(PROFESSOR_ID);
    }

    @ParameterizedTest(name = "[{index}] professor \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-uuid"})
    @DisplayName("a course without a usable professor is never sent: it reads as PROFESSOR_REQUIRED")
    void professorIsAlwaysExplicit(String professorId) {
        assertThatThrownBy(() -> create.execute(TOKEN, command(professorId)))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.PROFESSOR_REQUIRED));
        verify(client, never()).createCourse(any(), any());
    }

    @Test
    @DisplayName("updating converts the local weekday and hour to UTC and sends only what changed")
    void updatesInUtc() {
        when(client.updateCourse(any(), any(), any())).thenReturn(COURSE);

        update.execute(TOKEN, COURSE_ID.toUpperCase(), new UpdateCourseCommand(
                null, null, DayOfWeek.MONDAY, LocalTime.of(23, 30), null, null, 20, "INACTIVE"));

        verify(client).updateCourse(TOKEN, COURSE_ID, new CourseChanges(
                null, null, DayOfWeek.TUESDAY, LocalTime.of(2, 30), null, null, 20, "INACTIVE"));
    }

    @Test
    @DisplayName("an update that does not move the class sends no weekday or time")
    void updateWithoutSchedule() {
        when(client.updateCourse(any(), any(), any())).thenReturn(COURSE);

        update.execute(TOKEN, COURSE_ID, new UpdateCourseCommand("Bachata", null, null, null, null, null, null, null));

        verify(client).updateCourse(TOKEN, COURSE_ID,
                new CourseChanges("Bachata", null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("the weekday and the time of an update travel together: one without the other is refused")
    void scheduleIsAPair() {
        assertThatThrownBy(() -> new UpdateCourseCommand(null, null, DayOfWeek.MONDAY, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateCourseCommand(null, null, null, LocalTime.NOON, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an id that is not a UUID is a course that does not exist, without calling the API")
    void invalidCourseId() {
        assertThatThrownBy(() -> update.execute(TOKEN, "../admin", new UpdateCourseCommand(
                "x", null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("without an access token nothing is sent: it reads as unavailable")
    void withoutToken(String token) {
        assertThatThrownBy(() -> list.execute(token)).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> create.execute(token, command(PROFESSOR_ID)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> update.execute(token, COURSE_ID, new UpdateCourseCommand(
                "x", null, null, null, null, null, null, null))).isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("failures of the API reach the caller as they are")
    void failuresPropagate() {
        when(client.listCourses(TOKEN)).thenThrow(new ServiceUnavailableException("down"));
        when(client.updateCourse(any(), any(), any())).thenThrow(new CourseManagementException(Code.COURSE_NOT_OWNED));

        assertThatThrownBy(() -> list.execute(TOKEN)).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> update.execute(TOKEN, COURSE_ID, new UpdateCourseCommand(
                "x", null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_OWNED));
    }
}
