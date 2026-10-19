package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ModuleReorderMismatchException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReorderVirtualModulesUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final ReorderVirtualModulesUseCaseImpl useCase =
        new ReorderVirtualModulesUseCaseImpl(courseRepository, moduleRepository, auditRepository);

    private static VirtualCourse course(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    @Test
    void reorders_modules_according_to_the_requested_list() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule first = VirtualModule.create(courseId, "M1", 0);
        VirtualModule second = VirtualModule.create(courseId, "M2", 1);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of(first, second));

        List<VirtualModuleManagementResult> results = useCase.reorder(
            courseId.toString(),
            new ReorderVirtualModulesCommand(List.of(second.getId().toString(), first.getId().toString())),
            ownerId, false
        );

        assertThat(results.get(0).moduleId()).isEqualTo(second.getId().toString());
        assertThat(results.get(0).order()).isZero();
        assertThat(results.get(1).moduleId()).isEqualTo(first.getId().toString());
        assertThat(results.get(1).order()).isEqualTo(1);
    }

    @Test
    void rejects_a_list_missing_a_current_module() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule first = VirtualModule.create(courseId, "M1", 0);
        VirtualModule second = VirtualModule.create(courseId, "M2", 1);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> useCase.reorder(
            courseId.toString(), new ReorderVirtualModulesCommand(List.of(first.getId().toString())),
            ownerId, false
        )).isInstanceOf(ModuleReorderMismatchException.class);
    }

    @Test
    void rejects_a_list_with_a_module_from_another_course() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule first = VirtualModule.create(courseId, "M1", 0);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of(first));

        assertThatThrownBy(() -> useCase.reorder(
            courseId.toString(),
            new ReorderVirtualModulesCommand(List.of(first.getId().toString(), UUID.randomUUID().toString())),
            ownerId, false
        )).isInstanceOf(ModuleReorderMismatchException.class);
    }
}
