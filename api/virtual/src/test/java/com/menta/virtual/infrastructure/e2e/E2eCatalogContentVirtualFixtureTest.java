package com.menta.virtual.infrastructure.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class E2eCatalogContentVirtualFixtureTest {

    @Test
    void creates_one_published_and_one_draft_fixture_when_absent() throws Exception {
        ListManagedVirtualCoursesUseCase list = mock(ListManagedVirtualCoursesUseCase.class);
        CreateVirtualCourseUseCase courses = mock(CreateVirtualCourseUseCase.class);
        CreateVirtualModuleUseCase modules = mock(CreateVirtualModuleUseCase.class);
        CreateVirtualLessonUseCase lessons = mock(CreateVirtualLessonUseCase.class);
        PublishVirtualCourseUseCase publish = mock(PublishVirtualCourseUseCase.class);
        when(list.list(any(), anyBoolean())).thenReturn(List.of());
        when(courses.create(any(), any(), anyBoolean())).thenReturn(
            course("published-id", E2eCatalogContentVirtualFixture.PUBLISHED_TITLE),
            course("draft-id", E2eCatalogContentVirtualFixture.DRAFT_TITLE)
        );
        when(modules.create(anyString(), any(), any(), anyBoolean()))
            .thenReturn(new VirtualModuleManagementResult("module-id", "course-id", "Module", true, 0));

        new E2eCatalogContentVirtualFixture(list, courses, modules, lessons, publish).run(mock());

        verify(courses, times(2)).create(any(), any(), anyBoolean());
        verify(modules, times(2)).create(anyString(), any(), any(), anyBoolean());
        verify(lessons, times(2)).create(anyString(), any(), any(), anyBoolean());
        verify(publish).publish(eq("published-id"), any(), anyBoolean());
    }

    @Test
    void does_not_duplicate_existing_fixture_courses() throws Exception {
        ListManagedVirtualCoursesUseCase list = mock(ListManagedVirtualCoursesUseCase.class);
        when(list.list(any(), anyBoolean())).thenReturn(List.of(
            course("published-id", E2eCatalogContentVirtualFixture.PUBLISHED_TITLE),
            course("draft-id", E2eCatalogContentVirtualFixture.DRAFT_TITLE)
        ));
        CreateVirtualCourseUseCase courses = mock(CreateVirtualCourseUseCase.class);

        new E2eCatalogContentVirtualFixture(
            list, courses, mock(CreateVirtualModuleUseCase.class), mock(CreateVirtualLessonUseCase.class),
            mock(PublishVirtualCourseUseCase.class)
        ).run(mock());

        verify(courses, never()).create(any(), any(), anyBoolean());
    }

    private static VirtualCourseManagementResult course(String id, String title) {
        return new VirtualCourseManagementResult(id, title, "short", "description", "professor", "image",
            "DANCE", "BEGINNER", false, "DRAFT");
    }
}
