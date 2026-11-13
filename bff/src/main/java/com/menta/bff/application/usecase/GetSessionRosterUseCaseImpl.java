package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.RosterStudent;
import com.menta.bff.application.dto.SessionRoster;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.application.usecase.SessionRosterView.Outcome;
import java.text.Normalizer;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetSessionRosterUseCase}. Who is in the roster, and who already checked in, is
 * decided by the Physical API; this class only filters it by name and words it. A failed lookup is logged
 * without token, identifiers or rows (they hold the students' names) and reads as an outcome of the view.
 */
@Slf4j
@RequiredArgsConstructor
public class GetSessionRosterUseCaseImpl implements GetSessionRosterUseCase {

    /** The search box allows no more; the rest is cut, never sent anywhere. */
    static final int MAX_TEXT_LENGTH = 100;

    private static final String PRESENT = "Presente";
    private static final String PENDING = "Pendiente";

    private final PhysicalReceptionApiClient receptionApiClient;
    private final ZoneId displayZone;

    @Override
    public SessionRosterView execute(String accessToken, String sessionId, String text) {
        String search = normalizeText(text);
        String id = UuidFormat.normalize(sessionId);
        if (id == null) {
            return SessionRosterView.failed(Outcome.NOT_FOUND, search);
        }
        if (accessToken == null || accessToken.isBlank()) {
            return SessionRosterView.failed(Outcome.UNAVAILABLE, search);
        }
        try {
            return toView(receptionApiClient.getRoster(accessToken, id), search);
        } catch (PhysicalReceptionApiClient.SessionNotFoundException e) {
            return SessionRosterView.failed(Outcome.NOT_FOUND, search);
        } catch (PhysicalReceptionApiClient.ServiceUnavailableException e) {
            log.warn("Session roster unavailable, rendering the screen without it: {}", e.getMessage());
            return SessionRosterView.failed(Outcome.UNAVAILABLE, search);
        }
    }

    private SessionRosterView toView(SessionRoster roster, String search) {
        List<RosterStudent> students = roster.students();
        int present = (int) students.stream().filter(RosterStudent::present).count();
        List<SessionRosterView.Row> rows = students.stream()
                .filter(student -> matches(student.displayName(), search))
                .map(student -> toRow(student, roster.cancelled()))
                .toList();
        return new SessionRosterView(Outcome.OK, roster.courseName(),
                ClassLabels.moment(roster.scheduledAt(), displayZone), roster.cancelled(), roster.capacity(),
                students.size(), present, search, rows);
    }

    private SessionRosterView.Row toRow(RosterStudent student, boolean sessionCancelled) {
        return new SessionRosterView.Row(student.studentId(), student.displayName(),
                assignmentLabel(student.assignmentType()), student.present(),
                student.present() ? PRESENT : PENDING,
                student.checkedInAt() == null ? null : ClassLabels.time(student.checkedInAt(), displayZone),
                student.present() ? kindLabel(student.checkInKind()) : null,
                !student.present() && !sessionCancelled);
    }

    private static String assignmentLabel(String assignmentType) {
        if (assignmentType == null) {
            return null;
        }
        return switch (assignmentType) {
            case "MONTHLY" -> "Plan mensual";
            case "INDIVIDUAL" -> "Clase individual";
            default -> null;
        };
    }

    private static String kindLabel(String kind) {
        if (kind == null) {
            return null;
        }
        return "QR".equals(kind) ? "QR" : "manual";
    }

    /** Trims, treats blank as no search and cuts to the box's limit without splitting a surrogate pair. */
    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            int end = Character.isHighSurrogate(trimmed.charAt(MAX_TEXT_LENGTH - 1))
                    ? MAX_TEXT_LENGTH - 1 : MAX_TEXT_LENGTH;
            trimmed = trimmed.substring(0, end).strip();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Every word of the search must be part of the name, whatever the case and the accents. */
    private static boolean matches(String displayName, String search) {
        if (search == null) {
            return true;
        }
        String name = fold(displayName);
        return Arrays.stream(fold(search).split("\\s+")).allMatch(name::contains);
    }

    private static String fold(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
