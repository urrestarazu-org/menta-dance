package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalSessionRosterView;
import com.menta.physical.application.dto.RosterAttendanceStatus;
import com.menta.physical.application.dto.SessionRosterRow;
import com.menta.physical.application.port.in.GetPhysicalSessionRosterUseCase;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.shared.auth.UserDisplayNamePort;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * #355. The roster comes only from the confirmed assignments of the session, never from holds, so a pending
 * payment cannot surface. The names of every student are asked once, in a single batch, through {@link
 * UserDisplayNamePort}; a student the user module does not know is shown as {@code "Alumno <first 8 chars of
 * the id>"} and only their count is logged, never an id or a name. The order is total and stable: display name
 * (Spanish collation, so case and accents do not matter) and then student id.
 */
@Slf4j
@RequiredArgsConstructor
public class GetPhysicalSessionRosterUseCaseImpl implements GetPhysicalSessionRosterUseCase {

    private static final Collator NAME_ORDER = Collator.getInstance(Locale.forLanguageTag("es"));
    private static final int SHORT_ID_LENGTH = 8;

    private final PhysicalSessionRepository sessionRepository;
    private final PhysicalCourseRepository courseRepository;
    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final UserDisplayNamePort displayNames;

    @Override
    public PhysicalSessionRosterView get(String sessionId) {
        PhysicalSession session = sessionRepository.findById(SessionId.of(sessionId))
            .orElseThrow(SessionNotFoundException::new);
        PhysicalCourse course = courseRepository.findById(session.getCourseId())
            .orElseThrow(CourseNotFoundException::new);
        List<SessionRosterRow> rows = assignmentRepository.findRoster(session.getId());

        List<PhysicalSessionRosterView.Student> students = toStudents(session, rows);
        return new PhysicalSessionRosterView(
            new PhysicalSessionRosterView.SessionHeader(
                session.getId().getValue(), session.getCourseId().getValue(), course.getTitle(),
                session.getScheduledAt(), session.getStatus(), session.getCapacity(), students.size()
            ),
            students
        );
    }

    private List<PhysicalSessionRosterView.Student> toStudents(PhysicalSession session, List<SessionRosterRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = displayNames.displayNamesOf(rows.stream().map(SessionRosterRow::studentId).toList());
        long unknown = rows.stream().filter(row -> !names.containsKey(row.studentId())).count();
        if (unknown > 0) {
            log.warn("Roster students unknown to the user module; sessionId={}, count={}", session.getId(), unknown);
        }
        return rows.stream()
            .map(row -> toStudent(row, names.getOrDefault(row.studentId(), neutralName(row.studentId()))))
            .sorted(Comparator
                .comparing(PhysicalSessionRosterView.Student::displayName, NAME_ORDER)
                .thenComparing(PhysicalSessionRosterView.Student::studentId))
            .toList();
    }

    private static PhysicalSessionRosterView.Student toStudent(SessionRosterRow row, String displayName) {
        RosterAttendanceStatus status =
            row.checkedInAt() == null ? RosterAttendanceStatus.PENDING : RosterAttendanceStatus.PRESENT;
        return new PhysicalSessionRosterView.Student(
            row.studentId(), displayName, row.assignmentType(), status, row.checkedInAt(), row.checkInKind()
        );
    }

    private static String neutralName(UUID studentId) {
        return "Alumno " + studentId.toString().substring(0, SHORT_ID_LENGTH);
    }
}
