package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserStatus;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.application.port.out.InstructorDisplayNameSource;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The use cases behind the course screens (#368): one course, the instructors to pick from, the caller's own. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Course screens use cases")
class CourseScreensUseCasesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OTHER_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";

    @Mock
    private PhysicalCourseManagementApiClient courseClient;
    @Mock
    private AdminUsersApiClient usersClient;
    @Mock
    private AccessTokenClaimsReader claimsReader;
    @Mock
    private InstructorDisplayNameSource nameSource;

    private static ManagedCourse course(String id) {
        return new ManagedCourse(id, "Salsa", "Nivel inicial", OTHER_ID, "Ada Lovelace", DayOfWeek.WEDNESDAY,
                LocalTime.of(1, 0), 60, "BEGINNER", 15, "ACTIVE");
    }

    private static UserSummary instructor(String id, String email, String first, String last) {
        return new UserSummary(id, email, first, last, UserRole.INSTRUCTOR, UserStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 1, 9, 30));
    }

    // ---- GetManagedCourse ----

    @Test
    @DisplayName("the course is found by id among the ones the API lets the caller see, worded in the academy's zone")
    void findsTheCourse() {
        when(courseClient.listCourses(TOKEN)).thenReturn(List.of(course(OTHER_ID), course(COURSE_ID)));

        ManagedCourseView view = new GetManagedCourseUseCaseImpl(courseClient, ZONE)
                .execute(TOKEN, COURSE_ID.toUpperCase());

        assertThat(view.courseId()).isEqualTo(COURSE_ID);
        assertThat(view.localDay()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(view.localTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("an id that is not a UUID is a course that does not exist, and the API is not asked")
    void malformedIdIsNotFound() {
        GetManagedCourseUseCase useCase = new GetManagedCourseUseCaseImpl(courseClient, ZONE);

        assertThatThrownBy(() -> useCase.execute(TOKEN, "../etc"))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
        verifyNoInteractions(courseClient);
    }

    @Test
    @DisplayName("a course the caller cannot see is a course that does not exist")
    void unknownIdIsNotFound() {
        when(courseClient.listCourses(TOKEN)).thenReturn(List.of(course(OTHER_ID)));

        assertThatThrownBy(() -> new GetManagedCourseUseCaseImpl(courseClient, ZONE).execute(TOKEN, COURSE_ID))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
    }

    // ---- ListInstructors ----

    @Test
    @DisplayName("lists the first 50 instructors, naming each one by name or, without a profile, by email")
    void listsInstructors() {
        when(usersClient.listUsers(eq(TOKEN), any())).thenReturn(new UserPage(List.of(
                instructor(COURSE_ID, "ada@example.com", "Ada", " Lovelace "),
                instructor(OTHER_ID, "sin@example.com", null, null)), 0, 50, 2, 1));

        InstructorOptions options = new ListInstructorsUseCaseImpl(usersClient).execute(TOKEN);

        ArgumentCaptor<UserListQuery> query = ArgumentCaptor.forClass(UserListQuery.class);
        verify(usersClient).listUsers(eq(TOKEN), query.capture());
        assertThat(query.getValue()).isEqualTo(new UserListQuery(UserRole.INSTRUCTOR, null, 0, 50));
        assertThat(options.options()).containsExactly(
                new InstructorOptions.Option(COURSE_ID, "Ada Lovelace"),
                new InstructorOptions.Option(OTHER_ID, "sin@example.com"));
        assertThat(options.truncated()).isFalse();
        assertThat(options.unavailable()).isFalse();
    }

    @Test
    @DisplayName("a full page is flagged as truncated when there are more instructors than shown")
    void truncated() {
        when(usersClient.listUsers(eq(TOKEN), any())).thenReturn(new UserPage(
                List.of(instructor(COURSE_ID, "ada@example.com", "Ada", "Lovelace")), 0, 50, 51, 2));

        assertThat(new ListInstructorsUseCaseImpl(usersClient).execute(TOKEN).truncated()).isTrue();
    }

    @Test
    @DisplayName("an API failure or a missing token reads as unavailable, never as an exception")
    void instructorsUnavailable() {
        when(usersClient.listUsers(eq(TOKEN), any()))
                .thenThrow(new AdminUsersApiClient.ServiceUnavailableException("down"))
                .thenThrow(new AdminUsersApiClient.InvalidRequestException("bad"));
        ListInstructorsUseCase useCase = new ListInstructorsUseCaseImpl(usersClient);

        assertThat(useCase.execute(TOKEN).unavailable()).isTrue();
        assertThat(useCase.execute(TOKEN).unavailable()).isTrue();
        assertThat(useCase.execute(" ").unavailable()).isTrue();
    }

    // ---- ResolveOwnInstructor ----

    @Test
    @DisplayName("the own instructor is the token's subject plus the name of the name source")
    void resolvesOwnInstructor() {
        when(claimsReader.subjectOf(TOKEN)).thenReturn(COURSE_ID);
        when(nameSource.displayNameOf(TOKEN)).thenReturn(Optional.of("Ada Lovelace"));

        assertThat(new ResolveOwnInstructorUseCaseImpl(claimsReader, nameSource).execute(TOKEN))
                .contains(new OwnInstructor(COURSE_ID, "Ada Lovelace"));
    }

    @Test
    @DisplayName("without a name there is no own instructor: a course is never created under an invented name")
    void noNameNoInstructor() {
        when(claimsReader.subjectOf(TOKEN)).thenReturn(COURSE_ID);
        when(nameSource.displayNameOf(TOKEN)).thenReturn(Optional.empty());

        assertThat(new ResolveOwnInstructorUseCaseImpl(claimsReader, nameSource).execute(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("a subject that is not a UUID, or an unreadable token, is no own instructor")
    void badSubject() {
        when(claimsReader.subjectOf(TOKEN)).thenReturn("1")
                .thenThrow(new AccessTokenClaimsReader.UnreadableTokenException("x"));
        ResolveOwnInstructorUseCase useCase = new ResolveOwnInstructorUseCaseImpl(claimsReader, nameSource);

        assertThat(useCase.execute(TOKEN)).isEmpty();
        assertThat(useCase.execute(TOKEN)).isEmpty();
        verifyNoInteractions(nameSource);
    }

    @Test
    @DisplayName("a blank name counts as no name")
    void blankName() {
        when(claimsReader.subjectOf(TOKEN)).thenReturn(COURSE_ID);
        when(nameSource.displayNameOf(TOKEN)).thenReturn(Optional.of("  "));

        assertThat(new ResolveOwnInstructorUseCaseImpl(claimsReader, nameSource).execute(TOKEN)).isEmpty();
    }
}
