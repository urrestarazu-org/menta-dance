package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PhysicalCourseAvailabilityPortImpl implements PhysicalCourseAvailabilityPort {

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;

    public PhysicalCourseAvailabilityPortImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<PhysicalCourseSummary> listCourses(String afterCursor, int pageSize) {
        CourseId cursor = afterCursor == null ? null : CourseId.of(afterCursor);
        List<PhysicalCourse> courses = courseRepository.findActive(cursor, pageSize);
        return courses.stream().map(PhysicalCourseAvailabilityPortImpl::toSummary).toList();
    }

    @Override
    public Optional<PhysicalCourseSummary> findActiveById(String courseId) {
        return courseRepository.findActiveById(CourseId.of(courseId))
            .map(PhysicalCourseAvailabilityPortImpl::toSummary);
    }

    @Override
    public Map<String, PhysicalCourseSummary> findActiveByIds(Collection<String> courseIds) {
        Objects.requireNonNull(courseIds, "courseIds");
        Map<String, CourseId> parsedByInput = new LinkedHashMap<>();
        for (String input : new LinkedHashSet<>(courseIds)) {
            try {
                parsedByInput.put(input, CourseId.of(input));
            } catch (IllegalArgumentException malformed) {
                // Null, blank or non-UUID: unresolvable, never a batch failure.
            }
        }
        if (parsedByInput.isEmpty()) {
            return Map.of();
        }

        Set<CourseId> distinctIds = new LinkedHashSet<>(parsedByInput.values());
        Map<CourseId, PhysicalCourseSummary> summariesById = courseRepository
            .findActiveByIds(distinctIds).stream()
            .collect(Collectors.toMap(
                PhysicalCourse::getId, PhysicalCourseAvailabilityPortImpl::toSummary
            ));

        Map<String, PhysicalCourseSummary> result = new LinkedHashMap<>();
        parsedByInput.forEach((input, id) -> {
            PhysicalCourseSummary summary = summariesById.get(id);
            if (summary != null) {
                result.put(input, summary);
            }
        });
        return Map.copyOf(result);
    }

    @Override
    public List<PhysicalSessionAvailability> listSessions(String courseId, Instant from, Instant to) {
        List<PhysicalSession> sessions = sessionRepository.findScheduled(CourseId.of(courseId), from, to);
        return sessions.stream().map(PhysicalCourseAvailabilityPortImpl::toAvailability).toList();
    }

    private static PhysicalCourseSummary toSummary(PhysicalCourse course) {
        return new PhysicalCourseSummary(
            course.getId().toString(),
            course.getTitle(),
            course.getProfessorName(),
            course.getDayOfWeek().name(),
            course.getStartTime().toString(),
            course.getLevel().name(),
            course.getCapacity()
        );
    }

    private static PhysicalSessionAvailability toAvailability(PhysicalSession session) {
        return new PhysicalSessionAvailability(
            session.getId().toString(),
            session.getCourseId().toString(),
            session.getScheduledAt().toString(),
            session.getCapacity(),
            session.getAssignedSpots(),
            session.getActiveCapacityHolds(),
            session.getAvailableSpots()
        );
    }
}
