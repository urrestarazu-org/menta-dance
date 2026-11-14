package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ManagedSession;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
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
@DisplayName("GetCourseSessionUseCaseImpl")
class GetCourseSessionUseCaseImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String SESSION_ID = "1b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OTHER_ID = "2b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final ManagedSession SESSION = new ManagedSession(SESSION_ID, COURSE_ID,
            Instant.parse("2026-11-18T01:00:00Z"), 15, 10, 2, 3, "SCHEDULED", "Traer zapatos");
    private static final ManagedSession OTHER = new ManagedSession(OTHER_ID, COURSE_ID,
            Instant.parse("2026-11-25T01:00:00Z"), 15, 0, 0, 15, "CANCELLED", null);

    @Mock
    private PhysicalCourseManagementApiClient client;

    private GetCourseSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCourseSessionUseCaseImpl(client, ZONE);
    }

    @Test
    @DisplayName("finds the session among the ones the API lists for the course, in every status, and words it")
    void findsTheSession() {
        when(client.listCourseSessions(TOKEN, COURSE_ID, null, null)).thenReturn(List.of(OTHER, SESSION));

        ManagedSessionView view = useCase.execute(TOKEN, COURSE_ID, SESSION_ID);

        assertThat(view.sessionId()).isEqualTo(SESSION_ID);
        assertThat(view.courseId()).isEqualTo(COURSE_ID);
        assertThat(view.localDate()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(view.localTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(view.momentLabel()).isEqualTo("martes 17 de noviembre a las 22:00");
        assertThat(view.occupied()).isEqualTo(12);
        assertThat(view.cancelled()).isFalse();
    }

    @Test
    @DisplayName("a cancelled session is found too, as cancelled")
    void findsACancelledSession() {
        when(client.listCourseSessions(TOKEN, COURSE_ID, null, null)).thenReturn(List.of(OTHER));

        assertThat(useCase.execute(TOKEN, COURSE_ID, OTHER_ID).cancelled()).isTrue();
    }

    @Test
    @DisplayName("the ids are compared as UUIDs, whatever their case or surrounding spaces")
    void idsAreNormalized() {
        when(client.listCourseSessions(TOKEN, COURSE_ID, null, null)).thenReturn(List.of(SESSION));

        assertThat(useCase.execute(TOKEN, " " + COURSE_ID.toUpperCase() + " ", SESSION_ID.toUpperCase()).sessionId())
                .isEqualTo(SESSION_ID);
    }

    @Test
    @DisplayName("a session that the course does not have is SESSION_NOT_FOUND, even if it is another course's")
    void notInTheCourse() {
        when(client.listCourseSessions(TOKEN, COURSE_ID, null, null)).thenReturn(List.of(OTHER));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.SESSION_NOT_FOUND));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-uuid", "123"})
    @DisplayName("a session id that is not a UUID is SESSION_NOT_FOUND without calling the API")
    void malformedSessionId(String sessionId) {
        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, sessionId))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.SESSION_NOT_FOUND));
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a course id that is not a UUID is COURSE_NOT_FOUND without calling the API")
    void malformedCourseId() {
        assertThatThrownBy(() -> useCase.execute(TOKEN, "nope", SESSION_ID))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("without an access token it reads as unavailable, without calling the API")
    void noToken(String token) {
        assertThatThrownBy(() -> useCase.execute(token, COURSE_ID, SESSION_ID))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("the refusals of the API reach the caller untouched, such as COURSE_NOT_OWNED")
    void refusalsPass() {
        when(client.listCourseSessions(TOKEN, COURSE_ID, null, null))
                .thenThrow(new CourseManagementException(Code.COURSE_NOT_OWNED));

        assertThatThrownBy(() -> useCase.execute(TOKEN, COURSE_ID, SESSION_ID))
                .isInstanceOfSatisfying(CourseManagementException.class,
                        e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_OWNED));
        verify(client).listCourseSessions(TOKEN, COURSE_ID, null, null);
    }
}
