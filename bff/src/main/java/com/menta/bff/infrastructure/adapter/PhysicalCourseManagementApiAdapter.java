package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menta.bff.application.dto.CourseChanges;
import com.menta.bff.application.dto.CourseCreation;
import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.ManagedSession;
import com.menta.bff.application.dto.SessionChanges;
import com.menta.bff.application.dto.SessionCreation;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the management of physical courses and sessions (#368): the calls of the Physical API
 * under {@code /api/v1/admin/physical}, as {@code physical-v1.yaml} describes them.
 *
 * <p>The calls send {@code Authorization: Bearer <token>}. The token only ever travels in that header: it is
 * never put in a log line or an exception message, and neither are identifiers, names, notes or any response.
 * A refusal with one of the expected codes (400, 403, 404 or 409) becomes a
 * {@link PhysicalCourseManagementApiClient.CourseManagementException}; every other outcome (401, 429, 5xx, an
 * unexpected status or code, an unreadable or incomplete body, a transport error, a timeout) becomes a
 * {@link PhysicalCourseManagementApiClient.ServiceUnavailableException}, as the other authenticated adapters do.
 * </p>
 *
 * <p>The only fields read from an error body are the {@code code} and, for {@code SESSION_ALREADY_EXISTS}, the
 * {@code conflictingDates}; the rest is released unread. Requests send only the fields that are present, so a
 * partial update never overwrites what it does not name.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit constructor because
 * {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class PhysicalCourseManagementApiAdapter implements PhysicalCourseManagementApiClient {

    private static final String COURSES_ENDPOINT = "/api/v1/admin/physical/courses";
    private static final String COURSE_ENDPOINT = COURSES_ENDPOINT + "/{courseId}";
    private static final String COURSE_SESSIONS_ENDPOINT = COURSE_ENDPOINT + "/sessions";
    private static final String BATCH_ENDPOINT = COURSE_SESSIONS_ENDPOINT + "/batch";
    private static final String SESSION_ENDPOINT = "/api/v1/admin/physical/sessions/{sessionId}";
    private static final Set<Integer> REFUSAL_STATUSES = Set.of(400, 403, 404, 409);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    private final WebClient webClient;
    private final VirtualApiProperties properties;

    public PhysicalCourseManagementApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient, VirtualApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public List<ManagedCourse> listCourses(String accessToken) {
        return call("list courses", webClient.get().uri(COURSES_ENDPOINT), accessToken, 200, CoursesWire.class,
                PhysicalCourseManagementApiAdapter::toCourses);
    }

    @Override
    public ManagedCourse createCourse(String accessToken, CourseCreation creation) {
        CourseWriteWire body = new CourseWriteWire(creation.title(), creation.description(), creation.professorId(),
                creation.professorName(), creation.dayOfWeek().name(), TIME.format(creation.startTime()),
                creation.durationMinutes(), creation.level(), creation.capacity(), null);
        return call("create course", webClient.post().uri(COURSES_ENDPOINT).bodyValue(body), accessToken, 201,
                CourseWire.class, PhysicalCourseManagementApiAdapter::toCourse);
    }

    @Override
    public ManagedCourse updateCourse(String accessToken, String courseId, CourseChanges changes) {
        CourseWriteWire body = new CourseWriteWire(changes.title(), changes.description(), null, null,
                changes.dayOfWeek() == null ? null : changes.dayOfWeek().name(),
                changes.startTime() == null ? null : TIME.format(changes.startTime()), changes.durationMinutes(),
                changes.level(), changes.capacity(), changes.status());
        return call("update course", webClient.patch().uri(COURSE_ENDPOINT, courseId).bodyValue(body), accessToken,
                200, CourseWire.class, PhysicalCourseManagementApiAdapter::toCourse);
    }

    @Override
    public List<ManagedSession> listCourseSessions(String accessToken, String courseId, Instant from, Instant to) {
        WebClient.RequestHeadersSpec<?> request = webClient.get().uri(uriBuilder -> {
            uriBuilder.path(COURSE_SESSIONS_ENDPOINT);
            if (from != null) {
                uriBuilder.queryParam("from", from.toString());
            }
            if (to != null) {
                uriBuilder.queryParam("to", to.toString());
            }
            return uriBuilder.build(courseId);
        });
        return call("list course sessions", request, accessToken, 200, SessionsWire.class,
                PhysicalCourseManagementApiAdapter::toSessions);
    }

    @Override
    public ManagedSession createSession(String accessToken, String courseId, SessionCreation creation) {
        SessionWriteWire body = new SessionWriteWire(creation.date().toString(), TIME.format(creation.startTime()),
                creation.capacity(), creation.notes(), null);
        return call("create session", webClient.post().uri(COURSE_SESSIONS_ENDPOINT, courseId).bodyValue(body),
                accessToken, 201, SessionWire.class, PhysicalCourseManagementApiAdapter::toSession);
    }

    @Override
    public List<ManagedSession> createSessionsInBatch(
            String accessToken, String courseId, LocalDate fromDate, LocalDate toDate) {
        BatchWire body = new BatchWire(fromDate.toString(), toDate.toString());
        return call("create sessions in batch", webClient.post().uri(BATCH_ENDPOINT, courseId).bodyValue(body),
                accessToken, 201, SessionsWire.class, PhysicalCourseManagementApiAdapter::toSessions);
    }

    @Override
    public ManagedSession updateSession(String accessToken, String sessionId, SessionChanges changes) {
        SessionWriteWire body = new SessionWriteWire(
                changes.date() == null ? null : changes.date().toString(),
                changes.startTime() == null ? null : TIME.format(changes.startTime()), changes.capacity(),
                changes.notes(), changes.status());
        return call("update session", webClient.patch().uri(SESSION_ENDPOINT, sessionId).bodyValue(body),
                accessToken, 200, SessionWire.class, PhysicalCourseManagementApiAdapter::toSession);
    }

    /**
     * Sends the request with the bearer token and maps the answer: {@code okStatus} is the success, the expected
     * refusals are typed, everything else is unavailable.
     */
    private <W, R> R call(String operation, WebClient.RequestHeadersSpec<?> request, String accessToken,
            int okStatus, Class<W> wireType, Function<W, R> mapper) {
        try {
            W wire = request
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(response -> read(response, okStatus, wireType))
                    .timeout(properties.getTimeout())
                    .block();
            if (wire == null) {
                throw unavailable(operation + " answered without a body");
            }
            return mapper.apply(wire);
        } catch (CourseManagementException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) or the response may reach a log.
            log.warn("Physical API {} call failed: {}", operation, e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Physical API unavailable", e);
        }
    }

    /** Only {@code okStatus} is success; a refusal needs its code, any other error body is released unread. */
    private static <W> Mono<W> read(ClientResponse response, int okStatus, Class<W> wireType) {
        int status = response.statusCode().value();
        if (status == okStatus) {
            return response.bodyToMono(wireType);
        }
        if (REFUSAL_STATUSES.contains(status)) {
            return response.bodyToMono(ProblemWire.class)
                    .flatMap(problem -> Mono.<W>error(refusal(status, problem)))
                    .switchIfEmpty(Mono.error(unavailable("answered " + status + " without a body")));
        }
        return response.releaseBody().then(Mono.error(unavailable("answered " + status)));
    }

    private static RuntimeException refusal(int status, ProblemWire problem) {
        for (CourseManagementException.Code code : CourseManagementException.Code.values()) {
            if (code.name().equals(problem.code())) {
                boolean conflict = code == CourseManagementException.Code.SESSION_ALREADY_EXISTS
                        && problem.conflictingDates() != null;
                return new CourseManagementException(code, conflict ? problem.conflictingDates() : List.of());
            }
        }
        return unavailable("answered " + status);
    }

    private static List<ManagedCourse> toCourses(CoursesWire wire) {
        if (wire.courses() == null) {
            throw unavailable("courses list is incomplete");
        }
        return wire.courses().stream().map(PhysicalCourseManagementApiAdapter::toCourse).toList();
    }

    private static ManagedCourse toCourse(CourseWire item) {
        if (item == null || item.courseId() == null || item.title() == null || item.description() == null
                || item.professorId() == null || item.professorName() == null || item.dayOfWeek() == null
                || item.startTime() == null || item.durationMinutes() == null || item.level() == null
                || item.capacity() == null || item.status() == null) {
            throw unavailable("course is incomplete");
        }
        // An unknown weekday or an unreadable time throws a RuntimeException, which becomes unavailable.
        return new ManagedCourse(item.courseId(), item.title(), item.description(), item.professorId(),
                item.professorName(), DayOfWeek.valueOf(item.dayOfWeek()), LocalTime.parse(item.startTime()),
                item.durationMinutes(), item.level(), item.capacity(), item.status());
    }

    private static List<ManagedSession> toSessions(SessionsWire wire) {
        if (wire.sessions() == null) {
            throw unavailable("sessions list is incomplete");
        }
        return wire.sessions().stream().map(PhysicalCourseManagementApiAdapter::toSession).toList();
    }

    private static ManagedSession toSession(SessionWire item) {
        if (item == null || item.sessionId() == null || item.courseId() == null || item.scheduledAt() == null
                || item.capacity() == null || item.assignedSpots() == null || item.activeCapacityHolds() == null
                || item.availableSpots() == null || item.status() == null) {
            throw unavailable("session is incomplete");
        }
        return new ManagedSession(item.sessionId(), item.courseId(), item.scheduledAt(), item.capacity(),
                item.assignedSpots(), item.activeCapacityHolds(), item.availableSpots(), item.status(), item.notes());
    }

    private static ServiceUnavailableException unavailable(String reason) {
        return new ServiceUnavailableException("Physical API unavailable: " + reason);
    }

    /** Response of the courses list. */
    private record CoursesWire(List<CourseWire> courses) {
    }

    /** {@code PhysicalCourseManagementResponse}. */
    private record CourseWire(
            String courseId, String title, String description, String professorId, String professorName,
            String dayOfWeek, String startTime, Integer durationMinutes, String level, Integer capacity,
            String status) {
    }

    /** Response of the sessions lists. */
    private record SessionsWire(List<SessionWire> sessions) {
    }

    /** {@code PhysicalSessionManagementResponse}: only the fields the BFF shows are read. */
    private record SessionWire(
            String sessionId, String courseId, Instant scheduledAt, Integer capacity, Integer assignedSpots,
            Integer activeCapacityHolds, Integer availableSpots, String status, String notes) {
    }

    /** Create and update course bodies; fields that are {@code null} are not sent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CourseWriteWire(
            String title, String description, String professorId, String professorName, String dayOfWeek,
            String startTime, Integer durationMinutes, String level, Integer capacity, String status) {
    }

    /** Create and update session bodies; fields that are {@code null} are not sent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SessionWriteWire(String date, String startTime, Integer capacity, String notes, String status) {
    }

    /** Body of the batch creation. */
    private record BatchWire(String fromDate, String toDate) {
    }

    /** The only fields read from an error body. */
    private record ProblemWire(String code, List<Instant> conflictingDates) {
    }
}
