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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link ListLibraryVideosUseCase}; a plain read, registered without a transactional decorator.
 *
 * <p>Both roles are served from the same walk of the library's pages (at most {@value #MAX_WALK_PAGES} pages of
 * {@value #WALK_PAGE_SIZE}; a library too big for that is reported as unavailable instead of guessed). The library
 * has no "these ids" query, so there is nothing cheaper. Paging is then done in memory over the result.</p>
 *
 * <p>An admin sees everything the database knows plus the whole library: every video of the library (in the
 * library's order, with or without lessons) followed by the video ids that lessons of any course use and the
 * library no longer has, flagged as missing. An instructor's set is derived from their courses
 * ({@code findByProfessorId}), so the library is only asked for the metadata of those videos, never who owns them;
 * the walk stops as soon as all of them are found and their lessons are the only ones listed. In both cases a
 * missing video is only reported when no text filter is active (then not matching is not the same as not
 * existing).</p>
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
        Map<String, List<VideoLessonUsage>> usages = assemble(lessons, courses, modules);
        return pageOf(query, usages, walk(query, null), true);
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
        Map<String, List<VideoLessonUsage>> usages = assemble(lessons, courses, modules);
        if (usages.isEmpty()) {
            return new ManagedVideoPage(List.of(), query.page(), query.size(), 0);
        }
        return pageOf(query, usages, walk(query, usages.keySet()), false);
    }

    // --- walk and paging --------------------------------------------------

    /**
     * Joins the walked library videos with the lessons that use them, appends the used ids the library does not
     * have (only without a text filter) and cuts the requested page.
     *
     * @param includeUnused whether library videos no lesson uses are listed (admin) or dropped (instructor)
     */
    private static ManagedVideoPage pageOf(
        VideoLibraryQuery query, Map<String, List<VideoLessonUsage>> usages, Map<String, LibraryVideo> found,
        boolean includeUnused
    ) {
        List<ManagedVideo> all = new ArrayList<>();
        found.forEach((videoId, video) -> {
            if (includeUnused || usages.containsKey(videoId)) {
                all.add(new ManagedVideo(videoId, video, usages.getOrDefault(videoId, List.of())));
            }
        });
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

    /**
     * Walks the library's pages in its order. With {@code wanted} it keeps only those ids and stops once all are
     * found; with {@code null} (admin) it keeps every video and reads until the library is exhausted.
     */
    private Map<String, LibraryVideo> walk(VideoLibraryQuery query, Set<String> wanted) {
        Map<String, LibraryVideo> found = new LinkedHashMap<>();
        for (int index = 0; index < MAX_WALK_PAGES; index++) {
            LibraryVideoPage chunk =
                library.list(new VideoLibraryQuery(index, WALK_PAGE_SIZE, query.search(), query.order()));
            for (LibraryVideo video : chunk.items()) {
                if (wanted == null || wanted.contains(video.videoId())) {
                    found.putIfAbsent(video.videoId(), video);
                }
            }
            boolean everyWantedFound = wanted != null && found.size() == wanted.size();
            boolean exhausted = chunk.items().size() < WALK_PAGE_SIZE
                || (long) (index + 1) * WALK_PAGE_SIZE >= chunk.totalItems();
            if (everyWantedFound || exhausted) {
                return found;
            }
        }
        throw new VideoLibraryUnavailableException("The video library is too large to be listed");
    }

    // --- shared -----------------------------------------------------------

    private static Map<String, List<VideoLessonUsage>> assemble(
        List<VirtualLesson> lessons, Map<CourseId, VirtualCourse> courses, Map<ModuleId, VirtualModule> modules
    ) {
        Comparator<VirtualLesson> display = Comparator
            .comparing((VirtualLesson lesson) -> titleOf(courses.get(lesson.getCourseId())))
            .thenComparingInt(lesson -> orderOf(modules.get(lesson.getModuleId())))
            .thenComparingInt(VirtualLesson::getOrder)
            .thenComparing(VirtualLesson::getTitle);
        Map<String, List<VideoLessonUsage>> byVideo = new LinkedHashMap<>();
        lessons.stream().sorted(display).forEach(lesson -> byVideo
            .computeIfAbsent(lesson.getVideoId(), key -> new ArrayList<>())
            .add(new VideoLessonUsage(
                lesson.getCourseId().getValue().toString(), titleOf(courses.get(lesson.getCourseId())),
                lesson.getModuleId().getValue().toString(), moduleTitleOf(modules.get(lesson.getModuleId())),
                lesson.getId().getValue().toString(), lesson.getTitle()
            )));
        return byVideo;
    }

    private static String titleOf(VirtualCourse course) {
        return course == null ? "" : course.getTitle();
    }

    private static String moduleTitleOf(VirtualModule module) {
        return module == null ? "" : module.getTitle();
    }

    private static int orderOf(VirtualModule module) {
        return module == null ? Integer.MAX_VALUE : module.getOrder();
    }
}
