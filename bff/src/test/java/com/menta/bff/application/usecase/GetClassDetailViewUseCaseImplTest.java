package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.dto.PhysicalCoursePricing;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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

    @Mock
    private BillingApiClient billingApiClient;

    @Mock
    private PhysicalSessionsApiClient physicalSessionsApiClient;

    private GetClassDetailViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClassDetailViewUseCaseImpl(
                catalogApiClient, billingApiClient, physicalSessionsApiClient, ACADEMY_ZONE);
        lenient().when(billingApiClient.getPhysicalCoursePricing("p-1")).thenReturn(
                new PhysicalCoursePricing("p-1", new BigDecimal("25000.00"), "ARS"));
    }

    private static final String TOKEN = "access-token-abc";

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

        ClassDetailView view = useCase.execute("p-1", null);

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
        useCase = new GetClassDetailViewUseCaseImpl(
                catalogApiClient, billingApiClient, physicalSessionsApiClient, ZoneId.of("UTC"));
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of(
                new PhysicalCourseDetail.Session("s-1", Instant.parse("2026-10-12T21:00:00Z"), 12, 5))));

        assertThat(useCase.execute("p-1", null).sessions().get(0).whenLabel())
                .isEqualTo("lunes 12 de octubre a las 21:00");
    }

    @Test
    @DisplayName("reports no sessions for a course without upcoming ones")
    void reportsNoSessions() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of()));

        assertThat(useCase.execute("p-1", null).hasSessions()).isFalse();
    }

    @Test
    @DisplayName("lets a not-found course propagate so the page answers 404")
    void propagatesNotFound() {
        when(catalogApiClient.getPhysicalCourse("v-1"))
                .thenThrow(new CatalogApiClient.NotFoundException("nope"));

        assertThatThrownBy(() -> useCase.execute("v-1", null))
                .isInstanceOf(CatalogApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("degrades to an unavailable view when the catalog is down")
    void degradesWhenCatalogDown() {
        when(catalogApiClient.getPhysicalCourse("p-1"))
                .thenThrow(new CatalogApiClient.ServiceUnavailableException("down"));

        ClassDetailView view = useCase.execute("p-1", null);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.hasSessions()).isFalse();
        assertThat(view.title()).isNull();
        verifyNoInteractions(billingApiClient);
    }

    @Test
    @DisplayName("shows the published monthly price in es-AR with its currency")
    void showsPublishedPrice() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of()));

        ClassDetailView view = useCase.execute("p-1", null);

        assertThat(view.priceState()).isEqualTo(ClassDetailView.PriceState.PUBLISHED);
        assertThat(view.priceLabel()).startsWith("$").contains("25.000,00").endsWith(" por mes");
        assertThat(view.canQuote()).isTrue();
    }

    @Test
    @DisplayName("reports no published price on a billing 404, without failing the page")
    void noPublishedPrice() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of()));
        when(billingApiClient.getPhysicalCoursePricing("p-1"))
                .thenThrow(new BillingApiClient.NotFoundException("none"));

        ClassDetailView view = useCase.execute("p-1", null);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.title()).isEqualTo("Salsa en vivo");
        assertThat(view.priceState()).isEqualTo(ClassDetailView.PriceState.NOT_PUBLISHED);
        assertThat(view.priceLabel()).isNull();
        assertThat(view.canQuote()).isFalse();
    }

    @Test
    @DisplayName("reports the price as unavailable when billing is down, without failing the page")
    void priceUnavailableWhenBillingDown() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of()));
        when(billingApiClient.getPhysicalCoursePricing("p-1"))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("down"));

        ClassDetailView view = useCase.execute("p-1", null);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.priceState()).isEqualTo(ClassDetailView.PriceState.UNAVAILABLE);
        assertThat(view.canQuote()).isFalse();
    }

    private static PhysicalCourseDetail threeSessions() {
        return detail(List.of(
                new PhysicalCourseDetail.Session("s-1", Instant.parse("2026-10-12T21:00:00Z"), 12, 5),
                new PhysicalCourseDetail.Session("s-2", Instant.parse("2026-10-19T21:00:00Z"), 12, 0),
                new PhysicalCourseDetail.Session("s-3", Instant.parse("2026-10-26T21:00:00Z"), 12, 3)));
    }

    @Test
    @DisplayName("marks the sessions where the signed-in student already holds a spot")
    void marksOwnedSessions() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(threeSessions());
        when(physicalSessionsApiClient.findMySessionIds(TOKEN)).thenReturn(Set.of("s-2", "s-3", "s-other"));

        ClassDetailView view = useCase.execute("p-1", TOKEN);

        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::sessionId)
                .containsExactly("s-1", "s-2", "s-3");
        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::owned)
                .containsExactly(false, true, true);
        assertThat(view.unavailable()).isFalse();
    }

    @Test
    @DisplayName("marks nothing when the student holds none of the listed sessions")
    void marksNothingWhenNoneOwned() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(threeSessions());
        when(physicalSessionsApiClient.findMySessionIds(TOKEN)).thenReturn(Set.of());

        assertThat(useCase.execute("p-1", TOKEN).sessions())
                .extracting(ClassDetailView.SessionRow::owned).containsOnly(false);
    }

    @Test
    @DisplayName("an anonymous visitor gets no marks and no lookup is made")
    void anonymousIsNotLookedUp() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(threeSessions());

        ClassDetailView view = useCase.execute("p-1", null);

        assertThat(view.sessions()).extracting(ClassDetailView.SessionRow::owned).containsOnly(false);
        verifyNoInteractions(physicalSessionsApiClient);
    }

    @Test
    @DisplayName("a blank token is treated as anonymous")
    void blankTokenIsAnonymous() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(threeSessions());

        assertThat(useCase.execute("p-1", "  ").sessions())
                .extracting(ClassDetailView.SessionRow::owned).containsOnly(false);
        verifyNoInteractions(physicalSessionsApiClient);
    }

    @Test
    @DisplayName("renders the page without marks when the student's sessions cannot be read")
    void degradesWhenSessionsLookupFails() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(threeSessions());
        when(physicalSessionsApiClient.findMySessionIds(TOKEN))
                .thenThrow(new PhysicalSessionsApiClient.ServiceUnavailableException("down"));

        ClassDetailView view = useCase.execute("p-1", TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.title()).isEqualTo("Salsa en vivo");
        assertThat(view.sessions()).hasSize(3)
                .extracting(ClassDetailView.SessionRow::owned).containsOnly(false);
        assertThat(view.canQuote()).isTrue();
    }

    @Test
    @DisplayName("does not look sessions up when the course has none to mark")
    void skipsLookupWithoutSessions() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(detail(List.of()));

        useCase.execute("p-1", TOKEN);

        verifyNoInteractions(physicalSessionsApiClient);
    }

    @Test
    @DisplayName("sends the student's own token to look the sessions up")
    void usesTheStudentsToken() {
        when(catalogApiClient.getPhysicalCourse("p-1")).thenReturn(threeSessions());
        when(physicalSessionsApiClient.findMySessionIds(TOKEN)).thenReturn(Set.of());

        useCase.execute("p-1", TOKEN);

        verify(physicalSessionsApiClient).findMySessionIds(TOKEN);
    }
}
