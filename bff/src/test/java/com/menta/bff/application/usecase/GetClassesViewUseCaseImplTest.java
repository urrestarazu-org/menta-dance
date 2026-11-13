package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CatalogCourse;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetClassesViewUseCase")
class GetClassesViewUseCaseImplTest {

    private static final ZoneId ACADEMY_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private CatalogApiClient catalogApiClient;

    private GetClassesViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClassesViewUseCaseImpl(catalogApiClient, ACADEMY_ZONE);
    }

    @Test
    @DisplayName("lists only physical courses with level, professor, day and start time in Spanish, in the zone")
    void listsPhysicalCoursesOnly() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(
                new CatalogCourse("v-1", CourseModality.VIRTUAL, "Ballet", "BEGINNER", null, null, null),
                new CatalogCourse("p-1", CourseModality.PHYSICAL, "Salsa en vivo", "ADVANCED",
                        "María García", "MONDAY", "21:00")));

        ClassesView view = useCase.execute();

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        assertThat(view.classes()).containsExactly(new ClassCard(
                "p-1", "Salsa en vivo", "Avanzado", "María García", "Lunes 18:00"));
        assertThat(view.classes().get(0).detailPath()).isEqualTo("/classes/p-1");
    }

    @Test
    @DisplayName("shows the UTC recurrence in the academy zone, rolling the weekday back when needed")
    void convertsRecurrenceToAcademyZone() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(
                new CatalogCourse("a", CourseModality.PHYSICAL, "A", null, null, "TUESDAY", "23:00"),
                new CatalogCourse("b", CourseModality.PHYSICAL, "B", null, null, "WEDNESDAY", "01:00")));

        assertThat(useCase.execute().classes()).extracting(ClassCard::scheduleLabel)
                .containsExactly("Martes 20:00", "Martes 22:00");
    }

    @Test
    @DisplayName("tolerates a missing or unknown day and start time")
    void toleratesMissingSchedule() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(
                new CatalogCourse("a", CourseModality.PHYSICAL, "A", null, null, null, null),
                new CatalogCourse("b", CourseModality.PHYSICAL, "B", null, null, "FUNDAY", "13:00"),
                new CatalogCourse("c", CourseModality.PHYSICAL, "C", null, null, "SUNDAY", null)));

        ClassesView view = useCase.execute();

        assertThat(view.classes()).extracting(ClassCard::scheduleLabel)
                .containsExactly(null, "10:00", "Domingo");
    }

    @Test
    @DisplayName("reports empty when the catalog has no physical course")
    void reportsEmpty() {
        when(catalogApiClient.listCourses()).thenReturn(List.of(
                new CatalogCourse("v-1", CourseModality.VIRTUAL, "Ballet", "BEGINNER", null, null, null)));

        assertThat(useCase.execute().empty()).isTrue();
    }

    @Test
    @DisplayName("degrades to an unavailable view instead of throwing when the catalog is down")
    void degradesWhenCatalogDown() {
        when(catalogApiClient.listCourses())
                .thenThrow(new CatalogApiClient.ServiceUnavailableException("down"));

        ClassesView view = useCase.execute();

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.classes()).isEmpty();
    }
}
