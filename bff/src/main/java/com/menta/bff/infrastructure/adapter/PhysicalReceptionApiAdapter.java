package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CheckInReceipt;
import com.menta.bff.application.dto.RosterStudent;
import com.menta.bff.application.dto.SessionRoster;
import com.menta.bff.application.dto.StaffSession;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the reception screen's calls to the Physical API (#373): {@code GET
 * /api/v1/admin/physical/sessions}, {@code GET /api/v1/admin/physical/sessions/{sessionId}/roster} and the
 * {@code MANUAL} variant of {@code POST /api/v1/physical/sessions/{sessionId}/check-ins}.
 *
 * <p>The calls send {@code Authorization: Bearer <token>}. The token only ever travels in that header: it is
 * never put in a log line or an exception message, and neither are the identifiers of sessions and students,
 * the names of the roster or any response. Every outcome that is not a usable answer (400, 401, 403, 429, 5xx,
 * an unexpected status, an unreadable or incomplete body, a transport error, a timeout) becomes a
 * {@link PhysicalReceptionApiClient.ServiceUnavailableException}, as the other authenticated adapters do: the
 * page degrades the same way for all of them, and the API makes the real authorization decision.
 * </p>
 *
 * <p>The only error bodies read are the ones whose {@code code} the screen words: the {@code 404} of the roster
 * ({@code SESSION_NOT_FOUND}) and the {@code 403}/{@code 409} of the check-in. One field is read from them, the
 * {@code code}; every other error body is released unread.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit constructor because
 * {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class PhysicalReceptionApiAdapter implements PhysicalReceptionApiClient {

    private static final String SESSIONS_ENDPOINT = "/api/v1/admin/physical/sessions";
    private static final String ROSTER_ENDPOINT = SESSIONS_ENDPOINT + "/{sessionId}/roster";
    private static final String CHECK_INS_ENDPOINT = "/api/v1/physical/sessions/{sessionId}/check-ins";
    private static final String SESSION_NOT_FOUND_CODE = "SESSION_NOT_FOUND";
    private static final String NO_SPOT_CODE = "CAPACITY_ASSIGNMENT_REQUIRED";
    private static final String OUTSIDE_WINDOW_CODE = "OUTSIDE_CHECK_IN_WINDOW";
    private static final String NOT_ACTIVE_CODE = "SESSION_NOT_ACTIVE";
    private static final String CANCELLED = "CANCELLED";
    private static final String PRESENT = "PRESENT";
    private static final String MANUAL = "MANUAL";

    private final WebClient webClient;
    private final VirtualApiProperties properties;

    public PhysicalReceptionApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient, VirtualApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public List<StaffSession> listSessions(String accessToken, Instant from, Instant to) {
        try {
            SessionsWire wire = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SESSIONS_ENDPOINT)
                            .queryParam("from", "{from}")
                            .queryParam("to", "{to}")
                            .build(Map.of("from", from.toString(), "to", to.toString())))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(response -> readOk(response, SessionsWire.class))
                    .timeout(properties.getTimeout())
                    .block();
            return toSessions(wire);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, parameters) or the response may reach a log.
            log.warn("Physical API sessions call failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Physical API unavailable", e);
        }
    }

    @Override
    public SessionRoster getRoster(String accessToken, String sessionId) {
        try {
            RosterWire wire = webClient.get()
                    .uri(ROSTER_ENDPOINT, sessionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(this::readRoster)
                    .timeout(properties.getTimeout())
                    .block();
            return toRoster(wire);
        } catch (SessionNotFoundException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request or the response (names) may reach a log.
            log.warn("Physical API roster call failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Physical API unavailable", e);
        }
    }

    @Override
    public CheckInReceipt registerManualCheckIn(String accessToken, String sessionId, String studentId) {
        try {
            CheckInReceipt receipt = webClient.post()
                    .uri(CHECK_INS_ENDPOINT, sessionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ManualCheckInWire(MANUAL, studentId))
                    .exchangeToMono(this::readCheckIn)
                    .timeout(properties.getTimeout())
                    .block();
            if (receipt == null) {
                throw unavailable("check-in answered without a body");
            }
            return receipt;
        } catch (CheckInRejectedException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) or the response may reach a log.
            log.warn("Physical API check-in call failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Physical API unavailable", e);
        }
    }

    /** Only a 200 is success; the error body is never read: only the status decides. */
    private static <T> Mono<T> readOk(ClientResponse response, Class<T> type) {
        int status = response.statusCode().value();
        if (status == HttpStatus.OK.value()) {
            return response.bodyToMono(type);
        }
        return response.releaseBody().then(Mono.error(unavailable("answered " + status)));
    }

    /** Only a 200 is success; a 404 needs its code, any other error body is released unread. */
    private Mono<RosterWire> readRoster(ClientResponse response) {
        if (response.statusCode().value() != HttpStatus.NOT_FOUND.value()) {
            return readOk(response, RosterWire.class);
        }
        return response.bodyToMono(ProblemWire.class)
                .flatMap(problem -> Mono.<RosterWire>error(SESSION_NOT_FOUND_CODE.equals(problem.code())
                        ? new SessionNotFoundException("Physical API: the session does not exist")
                        : unavailable("answered 404")))
                .switchIfEmpty(Mono.error(unavailable("answered 404 without a body")));
    }

    /**
     * A 201 is the attendance recorded now and a 200 the replay of one that existed; both carry it. The
     * refusals the screen words need their code, any other error body is released unread.
     */
    private Mono<CheckInReceipt> readCheckIn(ClientResponse response) {
        int status = response.statusCode().value();
        if (status == HttpStatus.CREATED.value() || status == HttpStatus.OK.value()) {
            boolean replay = status == HttpStatus.OK.value();
            return response.bodyToMono(CheckInWire.class).map(wire -> toReceipt(wire, replay));
        }
        if (status == HttpStatus.FORBIDDEN.value() || status == HttpStatus.CONFLICT.value()) {
            return response.bodyToMono(ProblemWire.class)
                    .flatMap(problem -> Mono.<CheckInReceipt>error(rejection(status, problem.code())))
                    .switchIfEmpty(Mono.error(unavailable("answered " + status + " without a body")));
        }
        return response.releaseBody().then(Mono.error(unavailable("answered " + status)));
    }

    private static RuntimeException rejection(int status, String code) {
        if (status == HttpStatus.FORBIDDEN.value() && NO_SPOT_CODE.equals(code)) {
            return new CheckInRejectedException(CheckInRejectedException.Reason.CAPACITY_ASSIGNMENT_REQUIRED);
        }
        if (status == HttpStatus.FORBIDDEN.value() && OUTSIDE_WINDOW_CODE.equals(code)) {
            return new CheckInRejectedException(CheckInRejectedException.Reason.OUTSIDE_CHECK_IN_WINDOW);
        }
        if (status == HttpStatus.CONFLICT.value() && NOT_ACTIVE_CODE.equals(code)) {
            return new CheckInRejectedException(CheckInRejectedException.Reason.SESSION_NOT_ACTIVE);
        }
        return unavailable("answered " + status);
    }

    private static List<StaffSession> toSessions(SessionsWire wire) {
        if (wire == null || wire.sessions() == null) {
            throw unavailable("sessions list is incomplete");
        }
        return wire.sessions().stream().map(PhysicalReceptionApiAdapter::toSession).toList();
    }

    private static StaffSession toSession(SessionWire item) {
        if (item == null || item.sessionId() == null || item.courseName() == null || item.scheduledAt() == null
                || item.status() == null || item.capacity() == null || item.occupied() == null) {
            throw unavailable("session item is incomplete");
        }
        return new StaffSession(item.sessionId(), item.courseName(), item.scheduledAt(),
                CANCELLED.equals(item.status()), item.capacity(), item.occupied());
    }

    private static SessionRoster toRoster(RosterWire wire) {
        if (wire == null || wire.session() == null || wire.students() == null) {
            throw unavailable("roster is incomplete");
        }
        RosterSessionWire session = wire.session();
        if (session.sessionId() == null || session.courseName() == null || session.scheduledAt() == null
                || session.status() == null || session.capacity() == null) {
            throw unavailable("roster header is incomplete");
        }
        List<RosterStudent> students = wire.students().stream().map(PhysicalReceptionApiAdapter::toStudent).toList();
        return new SessionRoster(session.sessionId(), session.courseName(), session.scheduledAt(),
                CANCELLED.equals(session.status()), session.capacity(), students);
    }

    private static RosterStudent toStudent(StudentWire student) {
        if (student == null || student.studentId() == null || student.displayName() == null
                || student.attendanceStatus() == null) {
            throw unavailable("roster student is incomplete");
        }
        return new RosterStudent(student.studentId(), student.displayName(), student.assignmentType(),
                PRESENT.equals(student.attendanceStatus()), student.checkedInAt(), student.checkInKind());
    }

    private static CheckInReceipt toReceipt(CheckInWire wire, boolean replay) {
        if (wire.recordedAt() == null || wire.kind() == null) {
            throw unavailable("check-in response is incomplete");
        }
        return new CheckInReceipt(replay, wire.recordedAt(), wire.kind());
    }

    private static ServiceUnavailableException unavailable(String reason) {
        return new ServiceUnavailableException("Physical API unavailable: " + reason);
    }

    /** Wire shape of the agenda: only the fields the BFF shows are read. */
    private record SessionsWire(List<SessionWire> sessions) {
    }

    private record SessionWire(
            String sessionId, String courseName, Instant scheduledAt, String status, Integer capacity,
            Integer occupied) {
    }

    /** Wire shape of the roster: only the fields the BFF shows are read. */
    private record RosterWire(RosterSessionWire session, List<StudentWire> students) {
    }

    private record RosterSessionWire(
            String sessionId, String courseName, Instant scheduledAt, String status, Integer capacity) {
    }

    private record StudentWire(
            String studentId, String displayName, String assignmentType, String attendanceStatus,
            Instant checkedInAt, String checkInKind) {
    }

    /** Request body of the manual check-in, as {@code CheckInManualRequest} in {@code physical-v1.yaml} names it. */
    private record ManualCheckInWire(String type, String studentId) {
    }

    /** Response of the check-in: only the fields the screen words are read. */
    private record CheckInWire(Instant recordedAt, String kind) {
    }

    /** The only field read from an error body. */
    private record ProblemWire(String code) {
    }
}
