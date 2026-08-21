package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhysicalCourseOwnershipPortImplTest {

    @Mock private PhysicalCourseRepository courseRepository;

    private PhysicalCourseOwnershipPortImpl port;

    @BeforeEach
    void setUp() {
        port = new PhysicalCourseOwnershipPortImpl(courseRepository);
    }

    @Test
    void returns_the_professor_id_regardless_of_course_status() {
        CourseId id = CourseId.generate();
        UUID professorId = UUID.randomUUID();
        PhysicalCourse course = new PhysicalCourse(
            id, "Salsa inicial", "desc", professorId, "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20, CourseStatus.INACTIVE
        );
        when(courseRepository.findById(id)).thenReturn(Optional.of(course));

        Optional<UUID> result = port.findProfessorId(id.toString());

        assertThat(result).contains(professorId);
    }

    @Test
    void empty_when_the_course_does_not_exist() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(port.findProfessorId(id.toString())).isEmpty();
    }
}
