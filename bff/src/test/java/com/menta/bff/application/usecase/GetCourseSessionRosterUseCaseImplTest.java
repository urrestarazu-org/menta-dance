package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.SessionRosterView.Outcome;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCourseSessionRosterUseCaseImpl")
class GetCourseSessionRosterUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_ID = "1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OTHER_COURSE_ID = "9b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final ManagedSessionView SESSION = new ManagedSessionView(SESSION_ID, COURSE_ID,
            Instant.parse("2026-11-18T01:00:00Z"), LocalDate.of(2026, 11, 17), LocalTime.of(22, 0),
            "martes 17 de noviembre a las 22:00", false, 15, 2, 13, null);

    @Mock
    private GetCourseSessionUseCase getCourseSessionUseCase;

    @Mock
    private GetSessionRosterUseCase getSessionRosterUseCase;

    private GetCourseSessionRosterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCourseSessionRosterUseCaseImpl(getCourseSessionUseCase, getSessionRosterUseCase);
    }

    private static SessionRosterView roster(String courseId) {
        return new SessionRosterView(Outcome.OK, courseId, "Salsa", "martes 17 de noviembre a las 22:00", false, 15,
                2, 1, "ada", List.of());
    }

    @Test
    @DisplayName("looks the session up in the course first, then reads its roster with the same search text")
    void readsTheRosterOfASessionOfTheCourse() {
        SessionRosterView expected = roster(COURSE_ID);
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID)).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, "ada")).thenReturn(expected);

        SessionRosterView view = useCase.execute(TOKEN, COURSE_ID, SESSION_ID, "ada");

        assertThat(view).isSameAs(expected);
    }

    @Test
    @DisplayName("the course id of the roster is compared ignoring case")
    void courseIdIgnoresCase() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID)).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, null)).thenReturn(roster(COURSE_ID.toUpperCase()));

        assertThat(useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null).ok()).isTrue();
    }

    @Test
    @DisplayName("a roster that belongs to another course is a session not found: its rows never leave here")
    void rosterOfAnotherCourse() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID)).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, null)).thenReturn(roster(OTHER_COURSE_ID));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("a roster without a course id cannot be tied to the course: not found")
    void rosterWithoutCourseId() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID)).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, null)).thenReturn(roster(null));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("a session the course does not own never reaches the roster")
    void courseLookupFailureStopsBeforeTheRoster() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID))
                .thenThrow(new CourseManagementException(Code.COURSE_NOT_OWNED));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_OWNED));
        verifyNoInteractions(getSessionRosterUseCase);
    }

    @Test
    @DisplayName("an unavailable course lookup passes through and the roster is not read")
    void courseLookupUnavailable() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID))
                .thenThrow(new ServiceUnavailableException("down"));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(getSessionRosterUseCase);
    }

    @Test
    @DisplayName("the roster uses the session id the API listed, not the text of the URL")
    void usesTheListedSessionId() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID.toUpperCase())).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, null)).thenReturn(roster(COURSE_ID));

        useCase.execute(TOKEN, COURSE_ID, SESSION_ID.toUpperCase(), null);

        verify(getSessionRosterUseCase).execute(TOKEN, SESSION_ID, null);
    }

    @Test
    @DisplayName("a roster the API does not know is a session not found")
    void rosterNotFound() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID)).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, null))
                .thenReturn(SessionRosterView.failed(Outcome.NOT_FOUND, null));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("an unavailable roster is the unavailable failure of the management screens")
    void rosterUnavailable() {
        when(getCourseSessionUseCase.execute(TOKEN, COURSE_ID, SESSION_ID)).thenReturn(SESSION);
        when(getSessionRosterUseCase.execute(TOKEN, SESSION_ID, null))
                .thenReturn(SessionRosterView.failed(Outcome.UNAVAILABLE, null));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID, null))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
