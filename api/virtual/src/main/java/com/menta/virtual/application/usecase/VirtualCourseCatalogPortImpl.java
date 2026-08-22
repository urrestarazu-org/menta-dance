package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Optional;

public class VirtualCourseCatalogPortImpl implements VirtualCourseCatalogPort {

    private final VirtualCourseRepository virtualCourseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;

    public VirtualCourseCatalogPortImpl(
        VirtualCourseRepository virtualCourseRepository,
        VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository
    ) {
        this.virtualCourseRepository = virtualCourseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    public List<VirtualCourseSummary> listPublished(String afterCursor, int pageSize) {
        CourseId cursor = afterCursor == null ? null : CourseId.of(afterCursor);
        List<VirtualCourse> courses = virtualCourseRepository.findPublished(cursor, pageSize);
        return courses.stream().map(VirtualCourseCatalogPortImpl::toSummary).toList();
    }

    @Override
    public Optional<VirtualCourseSummary> findPublishedById(String courseId) {
        return virtualCourseRepository.findPublishedById(CourseId.of(courseId))
            .map(VirtualCourseCatalogPortImpl::toSummary);
    }

    /**
     * Builds the rich detail projection (#47). Steps:
     * <ol>
     *   <li>resolve the course aggregate via
     *       {@link VirtualCourseRepository#findPublishedById(CourseId)} — same
     *       single source of truth as the summary path, so the non-enumeration
     *       discipline (non-existent vs not published → {@code Optional.empty()})
     *       applies identically here;</li>
     *   <li>load ordered modules for that course via
     *       {@link VirtualModuleRepository#findByCourseId(CourseId)} (the
     *       repository already orders by display_order ascending, per
     *       {@code VirtualModuleRepository.findByCourseId});</li>
     *   <li>load ordered lessons per module via
     *       {@link VirtualLessonRepository#findByModuleId(com.menta.virtual.domain.model.ModuleId)},
     *       projecting each {@link VirtualLesson} to a
     *       {@link VirtualLessonSummary} WITHOUT {@code videoId};</li>
     *   <li>carry the aggregate's pre-computed counts through to
     *       {@link VirtualCourseStats} — no recomputation.</li>
     * </ol>
     * {@link VirtualLesson#isComplete()} is NOT used here: completion is
     * management logic (US-VIRTUAL-006 escenario 6), irrelevant to the public
     * detail read.
     */
    @Override
    public Optional<VirtualCourseDetailView> findPublishedDetailById(String courseId) {
        return virtualCourseRepository.findPublishedById(CourseId.of(courseId))
            .map(course -> toDetailView(course, modulesOf(course.getId())));
    }

    private List<VirtualModuleDetail> modulesOf(CourseId courseId) {
        List<VirtualModule> modules = moduleRepository.findByCourseId(courseId);
        return modules.stream()
            .map(module -> new VirtualModuleDetail(
                module.getId().toString(),
                module.getTitle(),
                module.getOrder(),
                lessonRepository.findByModuleId(module.getId()).stream()
                    .map(VirtualCourseCatalogPortImpl::toLessonSummary)
                    .toList()
            ))
            .toList();
    }

    private static VirtualCourseDetailView toDetailView(
        VirtualCourse course, List<VirtualModuleDetail> modules
    ) {
        return new VirtualCourseDetailView(
            course.getId().toString(),
            course.getTitle(),
            course.getDescription(),
            course.getImageUrl(),
            course.getCategory().getValue(),
            course.getLevel().name(),
            course.isPremium(),
            modules,
            new VirtualCourseStats(
                course.getModuleCount(),
                course.getLessonCount(),
                course.getTotalDurationMinutes()
            )
        );
    }

    private static VirtualLessonSummary toLessonSummary(VirtualLesson lesson) {
        return new VirtualLessonSummary(
            lesson.getId().toString(),
            lesson.getTitle(),
            lesson.getDurationMinutes(),
            lesson.isFree(),
            lesson.getOrder()
        );
    }

    private static VirtualCourseSummary toSummary(VirtualCourse course) {
        return new VirtualCourseSummary(
            course.getId().toString(),
            course.getTitle(),
            course.getShortDescription(),
            course.getImageUrl(),
            course.getCategory().getValue(),
            course.getLevel().name(),
            course.isPremium(),
            course.getModuleCount(),
            course.getLessonCount(),
            course.getTotalDurationMinutes()
        );
    }
}
