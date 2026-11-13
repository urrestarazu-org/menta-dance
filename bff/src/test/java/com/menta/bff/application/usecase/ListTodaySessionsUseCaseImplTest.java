package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.StaffSession;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListTodaySessionsUseCase")
class ListTodaySessionsUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private PhysicalReceptionApiClient receptionApiClient;

    private ListTodaySessionsUseCase useCaseAt(String instant, ZoneId zone) {
        return new ListTodaySessionsUseCaseImpl(
                receptionApiClient, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), zone);
    }

    private static StaffSession session(String start, String course, boolean cancelled) {
        return new StaffSession("id-" + start, course, Instant.parse(start), cancelled, 15, 12);
    }

    private Instant[] requestedRange() {
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(receptionApiClient).listSessions(eq(TOKEN), from.capture(), to.capture());
        return new Instant[] {from.getValue(), to.getValue()};
    }

    @Test
    @DisplayName("today is the local day in the academy's zone, sent as the UTC range [start, next start)")
    void asksForTheLocalDayAsUtc() {
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any())).thenReturn(List.of());

        useCaseAt("2026-11-13T15:30:00Z", BUENOS_AIRES).execute(TOKEN);

        assertThat(requestedRange()).containsExactly(
                Instant.parse("2026-11-13T03:00:00Z"), Instant.parse("2026-11-14T03:00:00Z"));
    }

    @Test
    @DisplayName("02:59:59Z is still the previous local day and 03:00:00Z is the next one: the boundary is exact")
    void dayChangesAtLocalMidnight() {
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any())).thenReturn(List.of());

        ReceptionSessionsView before = useCaseAt("2026-11-14T02:59:59Z", BUENOS_AIRES).execute(TOKEN);
        ReceptionSessionsView after = useCaseAt("2026-11-14T03:00:00Z", BUENOS_AIRES).execute(TOKEN);

        assertThat(before.dayLabel()).isEqualTo("viernes 13 de noviembre");
        assertThat(after.dayLabel()).isEqualTo("sábado 14 de noviembre");
    }

    @Test
    @DisplayName("sessions at 00:30 and 23:30 local land in today; midnight tomorrow and 23:59 yesterday do not")
    void boundarySessionsLandInTheRightDay() {
        // A faithful double of the API: [from, to) over scheduledAt, already ordered by start.
        List<StaffSession> catalog = List.of(
                session("2026-11-13T02:59:00Z", "Ayer 23:59", false),
                session("2026-11-13T03:30:00Z", "Madrugada 00:30", false),
                session("2026-11-14T02:30:00Z", "Noche 23:30", false),
                session("2026-11-14T03:00:00Z", "Mañana 00:00", false));
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any())).thenAnswer(call -> {
            Instant from = call.getArgument(1);
            Instant to = call.getArgument(2);
            return catalog.stream()
                    .filter(s -> !s.scheduledAt().isBefore(from) && s.scheduledAt().isBefore(to)).toList();
        });

        ReceptionSessionsView view = useCaseAt("2026-11-13T18:00:00Z", BUENOS_AIRES).execute(TOKEN);

        assertThat(view.rows()).extracting(ReceptionSessionsView.Row::courseName)
                .containsExactly("Madrugada 00:30", "Noche 23:30");
        assertThat(view.rows()).extracting(ReceptionSessionsView.Row::timeLabel).containsExactly("00:30", "23:30");
    }

    @Test
    @DisplayName("a zone with a daylight-saving change gets its real 25-hour day, not a fixed 24 hours")
    void daylightSavingDayIsNotAssumedToBe24Hours() {
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any())).thenReturn(List.of());

        useCaseAt("2026-10-25T12:00:00Z", ZoneId.of("Europe/Madrid")).execute(TOKEN);

        assertThat(requestedRange()).containsExactly(
                Instant.parse("2026-10-24T22:00:00Z"), Instant.parse("2026-10-25T23:00:00Z"));
    }

    @Test
    @DisplayName("words each session: course, start time in the academy's zone, cancelled flag and spots")
    void wordsTheRows() {
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any())).thenReturn(List.of(
                session("2026-11-13T21:00:00Z", "Salsa", false),
                session("2026-11-13T23:15:00Z", "Bachata", true)));

        ReceptionSessionsView view = useCaseAt("2026-11-13T15:30:00Z", BUENOS_AIRES).execute(TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        assertThat(view.dayLabel()).isEqualTo("viernes 13 de noviembre");
        assertThat(view.rows()).containsExactly(
                new ReceptionSessionsView.Row("id-2026-11-13T21:00:00Z", "Salsa",
                        Instant.parse("2026-11-13T21:00:00Z"), "18:00", false, 15, 12),
                new ReceptionSessionsView.Row("id-2026-11-13T23:15:00Z", "Bachata",
                        Instant.parse("2026-11-13T23:15:00Z"), "20:15", true, 15, 12));
    }

    @Test
    @DisplayName("a day without sessions is an empty, available view")
    void emptyDay() {
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any())).thenReturn(List.of());

        ReceptionSessionsView view = useCaseAt("2026-11-13T15:30:00Z", BUENOS_AIRES).execute(TOKEN);

        assertThat(view.empty()).isTrue();
        assertThat(view.unavailable()).isFalse();
    }

    @Test
    @DisplayName("an unavailable API reads as an unavailable view that keeps the day")
    void unavailableApi() {
        when(receptionApiClient.listSessions(eq(TOKEN), any(), any()))
                .thenThrow(new PhysicalReceptionApiClient.ServiceUnavailableException("boom"));

        ReceptionSessionsView view = useCaseAt("2026-11-13T15:30:00Z", BUENOS_AIRES).execute(TOKEN);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.rows()).isEmpty();
        assertThat(view.dayLabel()).isEqualTo("viernes 13 de noviembre");
    }

    @Test
    @DisplayName("without an access token the API is not asked and the view is unavailable")
    void withoutToken() {
        ReceptionSessionsView view = useCaseAt("2026-11-13T15:30:00Z", BUENOS_AIRES).execute(" ");

        assertThat(view.unavailable()).isTrue();
        verify(receptionApiClient, never()).listSessions(any(), any(), any());
    }
}
