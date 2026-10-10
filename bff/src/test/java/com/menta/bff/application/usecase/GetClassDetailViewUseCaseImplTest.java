package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.port.out.CatalogApiClient;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetClassDetailViewUseCase")
class GetClassDetailViewUseCaseImplTest {

    private static final ZoneId ACADEMY_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private CatalogApiClient catalogApiClient;

    private GetClassDetailViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClassDetailViewUseCaseImpl(catalogApiClient, ACADEMY_ZONE);
    }

    private static PhysicalCourseDetail detail(List<PhysicalCourseDetail.Session> sessions) {
        return new PhysicalCourseDetail("p-1", "Salsa en vivo", "ADVANCED", "María García",
                "MONDAY", "18:00", 12, sessions);
    }

    @Test
    @DisplayName("shows teacher, schedule and each session in the academy zone with free spots or Completo")
    void rendersSessionsInAcademyZone() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of(
                new PhysicalCourseDetail.Session("s-1", Instant.parse("2026-10-12T21:00:00Z"), 12, 5),
                new PhysicalCourseDetail.Session("s-2", Instant.parse("2026-10-14T01:30:00Z"), 12, 1),
                new PhysicalCourseDetail.Session("s-3", Instant.parse("2026-10-19T21:00:00Z"), 12, 0))));

        ClassDetailView view = useCase.execute("p-1");

        assertThat(view.unavailable()).isFalse();
        assertThat(view.courseId()).isEqualTo("p-1");
        assertThat(view.title()).isEqualTo("Salsa en vivo");
        assertThat(view.levelLabel()).isEqualTo("Avanzado");
        assertThat(view.professorName()).isEqualTo("María García");
        assertThat(view.scheduleLabel()).isEqualTo("Lunes 18:00");
        assertThat(view.hasSessions()).isTrue();
        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::whenLabel).containsExactly(
                "lunes 12 de octubre a las 18:00",
                "martes 13 de octubre a las 22:30",
                "lunes 19 de octubre a las 18:00");
        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::sessionId)
                .containsExactly("s-1", "s-2", "s-3");
        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::spotsLabel)
                .containsExactly("5 lugares libres", "1 lugar libre", "Completo");
        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::full)
                .containsExactly(false, false, true);
    }

    @Test
    @DisplayName("honours a different configured display zone")
    void honoursConfiguredZone() {
        useCase = new GetClassDetailViewUseCaseImpl(catalogApiClient, ZoneId.of("UTC"));
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of(
                new PhysicalCourseDetail.Session("s-1", Instant.parse("2026-10-12T21:00:00Z"), 12, 5))));

        assertThat(useCase.execute("p-1").sessions().get(0).whenLabel())
                .isEqualTo("lunes 12 de octubre a las 21:00");
    }

    @Test
    @DisplayName("reports no sessions for a course without upcoming ones")
    void reportsNoSessions() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of()));

        assertThat(useCase.execute("p-1").hasSessions()).isFalse();
    }

    @Test
    @DisplayName("lets a not-found course propagate so the page answers 404")
    void propagatesNotFound() {
        when(catalogApiClient.getPhysicalCourse("v-1"))
                .thenThrow(new CatalogApiClient.NotFoundException("nope"));

        assertThatThrownBy(() -> useCase.execute("v-1"))
                .isInstanceOf(CatalogApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("degrades to an unavailable view when the catalog is down")
    void degradesWhenCatalogDown() {
        when(catalogApiClient.getPhysicalCourse("p-1"))
                .thenThrow(new CatalogApiClient.ServiceUnavailableException("down"));

        ClassDetailView view = useCase.execute("p-1");

        assertThat(view.unavailable()).isTrue();
        assertThat(view.hasSessions()).isFalse();
        assertThat(view.title()).isNull();
    }
}
