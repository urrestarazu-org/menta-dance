package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CatalogCourse;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCatalogViewUseCase")
class GetCatalogViewUseCaseImplTest {

    private static final CatalogCourse VIRTUAL_COURSE =
            new CatalogCourse("v-1", CourseModality.VIRTUAL, "Ballet Básico", "BEGINNER", null, null, null);
    private static final CatalogCourse PHYSICAL_COURSE =
            new CatalogCourse("p-1", CourseModality.PHYSICAL, "Salsa en vivo", "ADVANCED",
                    "María García", "MONDAY", "18:00");

    @Mock
    private CatalogApiClient catalogApiClient;

    private GetCatalogViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCatalogViewUseCaseImpl(catalogApiClient);
    }

    @Test
    @DisplayName("maps a mixed catalog to cards in upstream order with Spanish labels")
    void mapsMixedCatalog() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(VIRTUAL_COURSE, PHYSICAL_COURSE));

        CatalogView view = useCase.execute(null);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        assertThat(view.selected()).isNull();
        assertThat(view.courses()).extracting(CatalogCard::courseId).containsExactly("v-1", "p-1");
        CatalogCard virtual = view.courses().get(0);
        assertThat(virtual.title()).isEqualTo("Ballet Básico");
        assertThat(virtual.modalityLabel()).isEqualTo("Virtual");
        assertThat(virtual.levelLabel()).isEqualTo("Principiante");
        assertThat(virtual.professorName()).isNull();
        CatalogCard physical = view.courses().get(1);
        assertThat(physical.modalityLabel()).isEqualTo("Presencial");
        assertThat(physical.levelLabel()).isEqualTo("Avanzado");
        assertThat(physical.professorName()).isEqualTo("María García");
    }

    @Test
    @DisplayName("links each course to the detail page of its own modality")
    void linksEachModalityToItsDetailPage() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(VIRTUAL_COURSE, PHYSICAL_COURSE));

        CatalogView view = useCase.execute(null);

        assertThat(view.courses().get(0).detailPath()).isEqualTo("/courses/v-1");
        assertThat(view.courses().get(1).detailPath()).isEqualTo("/classes/p-1");
    }

    @Test
    @DisplayName("maps intermediate level and tolerates unknown or missing levels")
    void mapsLevelLabels() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(
                new CatalogCourse("a", CourseModality.VIRTUAL, "A", "intermediate", null, null, null),
                new CatalogCourse("b", CourseModality.VIRTUAL, "B", "EXPERT", null, null, null),
                new CatalogCourse("c", CourseModality.VIRTUAL, "C", null, null, null, null)));

        CatalogView view = useCase.execute(null);

        assertThat(view.courses()).extracting(CatalogCard::levelLabel)
                .containsExactly("Intermedio", null, null);
    }

    @Test
    @DisplayName("filters by virtual modality")
    void filtersByVirtual() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(VIRTUAL_COURSE, PHYSICAL_COURSE));

        CatalogView view = useCase.execute("virtual");

        assertThat(view.selected()).isEqualTo(CourseModality.VIRTUAL);
        assertThat(view.courses()).extracting(CatalogCard::courseId).containsExactly("v-1");
    }

    @Test
    @DisplayName("filters by physical modality")
    void filtersByPhysical() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(VIRTUAL_COURSE, PHYSICAL_COURSE));

        CatalogView view = useCase.execute("PHYSICAL");

        assertThat(view.selected()).isEqualTo(CourseModality.PHYSICAL);
        assertThat(view.courses()).extracting(CatalogCard::courseId).containsExactly("p-1");
    }

    @Test
    @DisplayName("treats an unknown filter value as all courses")
    void unknownFilterShowsAll() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(VIRTUAL_COURSE, PHYSICAL_COURSE));

        CatalogView view = useCase.execute("hybrid");

        assertThat(view.selected()).isNull();
        assertThat(view.courses()).hasSize(2);
    }

    @Test
    @DisplayName("reports an empty catalog without marking it unavailable")
    void reportsEmptyCatalog() {
        when(catalogApiClient.listCourses()).thenReturn(List.of());

        CatalogView view = useCase.execute(null);

        assertThat(view.empty()).isTrue();
        assertThat(view.unavailable()).isFalse();
        assertThat(view.courses()).isEmpty();
    }

    @Test
    @DisplayName("reports empty when the filter excludes every course")
    void reportsEmptyWhenFilterExcludesEverything() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(VIRTUAL_COURSE));

        CatalogView view = useCase.execute("physical");

        assertThat(view.empty()).isTrue();
    }

    @Test
    @DisplayName("degrades to an unavailable view when the upstream is unavailable")
    void degradesWhenUpstreamUnavailable() {
        when(catalogApiClient.listCourses())
                .thenThrow(new CatalogApiClient.ServiceUnavailableException("boom: internal detail"));

        CatalogView view = useCase.execute("virtual");

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.courses()).isEmpty();
        assertThat(view.selected()).isEqualTo(CourseModality.VIRTUAL);
    }
}
