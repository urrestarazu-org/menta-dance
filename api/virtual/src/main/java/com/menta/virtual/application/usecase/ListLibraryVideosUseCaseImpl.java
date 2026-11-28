package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.ManagedVideo;
import com.menta.virtual.application.dto.ManagedVideoPage;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.port.in.ListLibraryVideosUseCase;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link ListLibraryVideosUseCase}; a plain read, registered without a transactional decorator.
 *
 * <p>Both roles are served from the same walk of the whole library's pages (at most {@value #MAX_WALK_PAGES} pages
 * of {@value #WALK_PAGE_SIZE}; a library too big for that is reported as unavailable instead of guessed). The
 * library has no "these ids" query, so there is nothing cheaper. Paging is then done in memory over the result.</p>
 *
 * <p>Both roles see every video of the library (in the library's order, with or without lessons) followed by the
 * video ids that the lessons in scope use and the library no longer has, flagged as missing. What differs is the
 * scope of those lessons: an admin gets the lessons of any course, an instructor only the lessons of their own
 * courses ({@code findByProfessorId}), so an instructor never sees the titles of another instructor's lessons or
 * courses; a video used only by someone else's lessons is listed without lessons. This is a temporary decision
 * (ADR-0049, #861): the per-instructor restriction of the listing will be revisited. In both cases a missing video
 * is only reported when no text filter is active (then not matching is not the same as not existing).</p>
 */
public class ListLibraryVideosUseCaseImpl implements ListLibraryVideosUseCase {

    static final int WALK_PAGE_SIZE = VideoLibraryQuery.MAX_PAGE_SIZE;
    static final int MAX_WALK_PAGES = 10;

    private final VideoLibraryPort library;
    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;

    public ListLibraryVideosUseCaseImpl(
        VideoLibraryPort library, VirtualCourseRepository courseRepository,
        VirtualModuleRepository moduleRepository, VirtualLessonRepository lessonRepository
    ) {
        this.library = library;
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    public ManagedVideoPage list(VideoLibraryQuery query, UUID actingUserId, boolean actingAsAdmin) {
        if (query.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must not exceed " + MAX_PAGE_SIZE);
        }
        return actingAsAdmin ? listAll(query) : listOwn(query, actingUserId);
    }

    // --- ADMIN ------------------------------------------------------------

    private ManagedVideoPage listAll(VideoLibraryQuery query) {
        List<VirtualLesson> lessons = lessonRepository.findAllWithVideo();
        Map<CourseId, VirtualCourse> courses = new HashMap<>();
        Map<ModuleId, VirtualModule> modules = new HashMap<>();
        if (!lessons.isEmpty()) {
            courseRepository.findByIds(lessons.stream().map(VirtualLesson::getCourseId).distinct().toList())
                .forEach(course -> courses.put(course.getId(), course));
            moduleRepository.findByIds(lessons.stream().map(VirtualLesson::getModuleId).distinct().toList())
                .forEach(module -> modules.put(module.getId(), module));
        }
        Map<String, List<VideoLessonUsage>> usages = VideoUsages.byVideo(lessons, courses, modules);
        return pageOf(query, usages, walk(query));
    }

    // --- INSTRUCTOR -------------------------------------------------------

    private ManagedVideoPage listOwn(VideoLibraryQuery query, UUID instructorId) {
        List<VirtualCourse> ownCourses = courseRepository.findByProfessorId(instructorId);
        Map<CourseId, VirtualCourse> courses = new HashMap<>();
        Map<ModuleId, VirtualModule> modules = new HashMap<>();
        List<VirtualLesson> lessons = new ArrayList<>();
        for (VirtualCourse course : ownCourses) {
            courses.put(course.getId(), course);
            moduleRepository.findByCourseId(course.getId()).forEach(module -> modules.put(module.getId(), module));
            lessonRepository.findByCourseId(course.getId()).stream()
                .filter(VirtualLesson::isComplete)
                .forEach(lessons::add);
        }
        Map<String, List<VideoLessonUsage>> usages = VideoUsages.byVideo(lessons, courses, modules);
        return pageOf(query, usages, walk(query));
    }

    // --- walk and paging --------------------------------------------------

    /**
     * Joins the walked library videos with the lessons that use them, appends the used ids the library does not
     * have (only without a text filter) and cuts the requested page. Every library video is listed, used or not.
     */
    private static ManagedVideoPage pageOf(
        VideoLibraryQuery query, Map<String, List<VideoLessonUsage>> usages, Map<String, LibraryVideo> found
    ) {
        List<ManagedVideo> all = new ArrayList<>();
        found.forEach((videoId, video) ->
            all.add(new ManagedVideo(videoId, video, usages.getOrDefault(videoId, List.of()))));
        if (query.search() == null) {
            usages.keySet().stream()
                .filter(videoId -> !found.containsKey(videoId))
                .sorted()
                .forEach(videoId -> all.add(new ManagedVideo(videoId, null, usages.get(videoId))));
        }
        long from = (long) query.page() * query.size();
        int start = (int) Math.min(from, all.size());
        int end = (int) Math.min(from + query.size(), all.size());
        return new ManagedVideoPage(all.subList(start, end), query.page(), query.size(), all.size());
    }

    /** Walks the library's pages in its order, keeping every video, until the library is exhausted. */
    private Map<String, LibraryVideo> walk(VideoLibraryQuery query) {
        Map<String, LibraryVideo> found = new LinkedHashMap<>();
        for (int index = 0; index < MAX_WALK_PAGES; index++) {
            LibraryVideoPage chunk =
                library.list(new VideoLibraryQuery(index, WALK_PAGE_SIZE, query.search(), query.order()));
            for (LibraryVideo video : chunk.items()) {
                found.putIfAbsent(video.videoId(), video);
            }
            boolean exhausted = chunk.items().size() < WALK_PAGE_SIZE
                || (long) (index + 1) * WALK_PAGE_SIZE >= chunk.totalItems();
            if (exhausted) {
                return found;
            }
        }
        throw new VideoLibraryUnavailableException("The video library is too large to be listed");
    }
}
