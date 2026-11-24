package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
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
import java.util.regex.Pattern;

/**
 * Shared by the video management use cases: turns lessons into the {@link VideoLessonUsage} rows the API shows
 * (course, module and lesson with their titles) and validates the video ids the API accepts.
 */
final class VideoUsages {

    /** What a video id may look like: the library's ids are GUIDs, and the id ends up in a request path. */
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private VideoUsages() {
    }

    /**
     * Returns the id when it is valid.
     *
     * @throws IllegalArgumentException if {@code videoId} is not a plain identifier
     */
    static String requireValidId(String videoId) {
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            throw new IllegalArgumentException("Not a valid video id");
        }
        return videoId;
    }

    /** The usages of the lessons, titles looked up in the repositories, in display order. */
    static List<VideoLessonUsage> of(
        List<VirtualLesson> lessons, VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository
    ) {
        if (lessons.isEmpty()) {
            return List.of();
        }
        Map<CourseId, VirtualCourse> courses = new HashMap<>();
        courseRepository.findByIds(lessons.stream().map(VirtualLesson::getCourseId).distinct().toList())
            .forEach(course -> courses.put(course.getId(), course));
        Map<ModuleId, VirtualModule> modules = new HashMap<>();
        moduleRepository.findByIds(lessons.stream().map(VirtualLesson::getModuleId).distinct().toList())
            .forEach(module -> modules.put(module.getId(), module));
        return byVideo(lessons, courses, modules).values().stream().flatMap(List::stream).toList();
    }

    /** Groups the usages by video id; within a video and overall they are in display order. */
    static Map<String, List<VideoLessonUsage>> byVideo(
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
