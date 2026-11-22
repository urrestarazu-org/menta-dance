package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Managed virtual courses list view")
class ManagedVirtualCoursesViewTest {

    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";

    @Mock
    private VirtualCourseManagementApiClient client;

    private static ManagedVirtualCourse course(VirtualCourseLevel level, VirtualCourseStatus status) {
        return new ManagedVirtualCourse(COURSE_ID, "Salsa online", "Corta", "Larga",
                "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "https://img.test/a.png", "Salsa", level, true, status);
    }

    private List<ManagedVirtualCourseRow> rows(VirtualCourseLevel level, VirtualCourseStatus status) {
        when(client.listManagedCourses(TOKEN)).thenReturn(List.of(course(level, status)));
        return new ListManagedVirtualCoursesUseCaseImpl(client).execute(TOKEN).rows();
    }

    @ParameterizedTest(name = "[{index}] {0} reads \"{1}\"")
    @CsvSource({"BEGINNER,Principiante", "INTERMEDIATE,Intermedio", "ADVANCED,Avanzado", "UNKNOWN,Sin dato"})
    @DisplayName("each level is worded in Spanish")
    void levelLabels(VirtualCourseLevel level, String label) {
        assertThat(rows(level, VirtualCourseStatus.DRAFT)).singleElement()
                .satisfies(row -> assertThat(row.levelLabel()).isEqualTo(label));
    }

    @ParameterizedTest(name = "[{index}] {0}: \"{1}\", publish {2}, unpublish {3}, delete {4}")
    @CsvSource({
        "DRAFT,Borrador,true,false,true",
        "PUBLISHED,Publicado,false,true,false",
        "ARCHIVED,Archivado,false,false,false",
        "UNKNOWN,Sin dato,false,false,false"})
    @DisplayName("each status is worded in Spanish and decides which actions the screen offers")
    void statusLabelsAndActions(VirtualCourseStatus status, String label, boolean publish, boolean unpublish,
            boolean delete) {
        assertThat(rows(VirtualCourseLevel.BEGINNER, status)).singleElement().satisfies(row -> {
            assertThat(row.statusLabel()).isEqualTo(label);
            assertThat(row.canPublish()).isEqualTo(publish);
            assertThat(row.canUnpublish()).isEqualTo(unpublish);
            assertThat(row.canDelete()).isEqualTo(delete);
        });
    }

    @Test
    @DisplayName("a row carries the identity, the title, the category and the premium flag")
    void rowData() {
        assertThat(rows(VirtualCourseLevel.ADVANCED, VirtualCourseStatus.PUBLISHED)).containsExactly(
                new ManagedVirtualCourseRow(COURSE_ID, "Salsa online", "Salsa", "Avanzado", "Publicado", true,
                        false, true, false));
    }

    @Test
    @DisplayName("the rows keep the API's order and a listing without courses is empty, not unavailable")
    void orderAndEmpty() {
        when(client.listManagedCourses(TOKEN)).thenReturn(List.of(
                course(VirtualCourseLevel.BEGINNER, VirtualCourseStatus.DRAFT),
                course(VirtualCourseLevel.ADVANCED, VirtualCourseStatus.ARCHIVED)));
        ListManagedVirtualCoursesUseCase list = new ListManagedVirtualCoursesUseCaseImpl(client);

        ManagedVirtualCoursesView view = list.execute(TOKEN);

        assertThat(view.rows()).extracting(ManagedVirtualCourseRow::statusLabel)
                .containsExactly("Borrador", "Archivado");
        assertThat(view.empty()).isFalse();
        assertThat(view.unavailable()).isFalse();

        when(client.listManagedCourses(TOKEN)).thenReturn(List.of());
        assertThat(list.execute(TOKEN).empty()).isTrue();
    }

    @Test
    @DisplayName("the unavailable view has no rows and is neither empty nor available")
    void unavailableView() {
        ManagedVirtualCoursesView view = ManagedVirtualCoursesView.unavailableView();

        assertThat(view.rows()).isEmpty();
        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
    }

    @Test
    @DisplayName("an API failure reaches the caller, which decides to show the unavailable view")
    void failureReachesTheCaller() {
        when(client.listManagedCourses(TOKEN)).thenThrow(new ServiceUnavailableException("down"));

        assertThatThrownBy(() -> new ListManagedVirtualCoursesUseCaseImpl(client).execute(TOKEN))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
