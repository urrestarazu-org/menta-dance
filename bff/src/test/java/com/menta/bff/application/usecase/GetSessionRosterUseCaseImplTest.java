package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.RosterStudent;
import com.menta.bff.application.dto.SessionRoster;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.application.usecase.SessionRosterView.Outcome;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetSessionRosterUseCase")
class GetSessionRosterUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String SESSION_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final Instant START = Instant.parse("2026-11-13T21:00:00Z");

    private static final RosterStudent ADA =
            new RosterStudent("s-1", "Ada Lovelace", "MONTHLY", true, Instant.parse("2026-11-13T21:05:00Z"), "QR");
    private static final RosterStudent JOSE =
            new RosterStudent("s-2", "José Pérez", "INDIVIDUAL", false, null, null);
    private static final RosterStudent MARIA =
            new RosterStudent("s-3", "María José Gómez", null, true, Instant.parse("2026-11-13T21:10:00Z"), "MANUAL");

    @Mock
    private PhysicalReceptionApiClient receptionApiClient;

    private GetSessionRosterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSessionRosterUseCaseImpl(receptionApiClient, ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    private void rosterOf(boolean cancelled, RosterStudent... students) {
        when(receptionApiClient.getRoster(TOKEN, SESSION_ID))
                .thenReturn(new SessionRoster(SESSION_ID, "Salsa", START, cancelled, 15, List.of(students)));
    }

    private static List<String> names(SessionRosterView view) {
        return view.rows().stream().map(SessionRosterView.Row::displayName).toList();
    }

    @Test
    @DisplayName("words the header and each student: status as text, time and kind of the check-in, spot type")
    void wordsTheRoster() {
        rosterOf(false, ADA, JOSE, MARIA);

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, null);

        assertThat(view.outcome()).isEqualTo(Outcome.OK);
        assertThat(view.ok()).isTrue();
        assertThat(view.courseName()).isEqualTo("Salsa");
        assertThat(view.momentLabel()).isEqualTo("viernes 13 de noviembre a las 18:00");
        assertThat(view.capacity()).isEqualTo(15);
        assertThat(view.enrolled()).isEqualTo(3);
        assertThat(view.present()).isEqualTo(2);
        assertThat(view.rows()).containsExactly(
                new SessionRosterView.Row(
                        "s-1", "Ada Lovelace", "Plan mensual", true, "Presente", "18:05", "QR", false),
                new SessionRosterView.Row(
                        "s-2", "José Pérez", "Clase individual", false, "Pendiente", null, null, true),
                new SessionRosterView.Row(
                        "s-3", "María José Gómez", null, true, "Presente", "18:10", "manual", false));
    }

    @Test
    @DisplayName("only a pending student of a session that is not cancelled can be registered")
    void cancelledSessionOffersNoRegistration() {
        rosterOf(true, ADA, JOSE);

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, null);

        assertThat(view.cancelled()).isTrue();
        assertThat(view.rows()).allSatisfy(row -> assertThat(row.canRegister()).isFalse());
    }

    @Test
    @DisplayName("the search ignores case and accents: 'jose' finds José Pérez and María José Gómez")
    void searchIgnoresCaseAndAccents() {
        rosterOf(false, ADA, JOSE, MARIA);

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, "  JOSE ");

        assertThat(names(view)).containsExactly("José Pérez", "María José Gómez");
        assertThat(view.text()).isEqualTo("JOSE");
        assertThat(view.filtered()).isTrue();
    }

    @Test
    @DisplayName("an accented search finds the unaccented name, and every word typed must be in the name")
    void searchIsAccentInsensitiveBothWaysAndByWords() {
        rosterOf(false, ADA, JOSE, MARIA);

        assertThat(names(useCase.execute(TOKEN, SESSION_ID, "pérez josé"))).containsExactly("José Pérez");
        assertThat(names(useCase.execute(TOKEN, SESSION_ID, "ada"))).containsExactly("Ada Lovelace");
        assertThat(names(useCase.execute(TOKEN, SESSION_ID, "lovelace gomez"))).isEmpty();
    }

    @Test
    @DisplayName("the totals do not change with the search, and no match is told apart from no students")
    void totalsStayAndNoMatchesIsNotNoStudents() {
        rosterOf(false, ADA, JOSE, MARIA);

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, "zzz");

        assertThat(view.rows()).isEmpty();
        assertThat(view.enrolled()).isEqualTo(3);
        assertThat(view.present()).isEqualTo(2);
        assertThat(view.noMatches()).isTrue();
        assertThat(view.noStudents()).isFalse();
    }

    @Test
    @DisplayName("a session without students says so, with or without search")
    void noStudents() {
        rosterOf(false);

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, null);

        assertThat(view.noStudents()).isTrue();
        assertThat(view.noMatches()).isFalse();
        assertThat(view.filtered()).isFalse();
    }

    @ParameterizedTest(name = "[{index}] blank search \"{0}\" is no search")
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("a blank search is no filter")
    void blankSearchIsNoFilter(String text) {
        rosterOf(false, ADA, JOSE);

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, text);

        assertThat(view.rows()).hasSize(2);
        assertThat(view.text()).isNull();
    }

    @Test
    @DisplayName("a search longer than the box allows is cut, and a surrogate pair is not split")
    void longSearchIsCut() {
        rosterOf(false, ADA);
        String text = "a".repeat(GetSessionRosterUseCaseImpl.MAX_TEXT_LENGTH - 1) + "😀" + "zzz";

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, text);

        assertThat(view.text()).hasSize(GetSessionRosterUseCaseImpl.MAX_TEXT_LENGTH - 1);
    }

    @Test
    @DisplayName("an id that is not a UUID is not found and the API is not asked")
    void malformedIdIsNotFound() {
        SessionRosterView view = useCase.execute(TOKEN, "../etc/passwd", "ada");

        assertThat(view.outcome()).isEqualTo(Outcome.NOT_FOUND);
        assertThat(view.text()).isEqualTo("ada");
        verify(receptionApiClient, never()).getRoster(any(), any());
    }

    @Test
    @DisplayName("the id is normalized before it goes to the API")
    void idIsNormalized() {
        rosterOf(false, ADA);

        SessionRosterView view = useCase.execute(TOKEN, " " + SESSION_ID.toUpperCase() + " ", null);

        assertThat(view.outcome()).isEqualTo(Outcome.OK);
    }

    @Test
    @DisplayName("a session the API does not know is not found")
    void unknownSession() {
        when(receptionApiClient.getRoster(TOKEN, SESSION_ID))
                .thenThrow(new PhysicalReceptionApiClient.SessionNotFoundException("gone"));

        assertThat(useCase.execute(TOKEN, SESSION_ID, null).outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    @DisplayName("an unavailable API reads as an unavailable view")
    void unavailableApi() {
        when(receptionApiClient.getRoster(TOKEN, SESSION_ID))
                .thenThrow(new PhysicalReceptionApiClient.ServiceUnavailableException("boom"));

        SessionRosterView view = useCase.execute(TOKEN, SESSION_ID, "ada");

        assertThat(view.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(view.ok()).isFalse();
        assertThat(view.rows()).isEmpty();
        assertThat(view.text()).isEqualTo("ada");
    }

    @Test
    @DisplayName("without an access token the API is not asked and the view is unavailable")
    void withoutToken() {
        assertThat(useCase.execute(null, SESSION_ID, null).outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(useCase.execute(" ", SESSION_ID, null).outcome()).isEqualTo(Outcome.UNAVAILABLE);
        verify(receptionApiClient, never()).getRoster(any(), any());
    }

    @Test
    @DisplayName("an unknown spot type or kind is worded without failing")
    void unknownLabels() {
        rosterOf(false,
                new RosterStudent("s-9", "Zoe", "SOMETHING_NEW", true, Instant.parse("2026-11-13T21:05:00Z"), null));

        SessionRosterView.Row row = useCase.execute(TOKEN, SESSION_ID, null).rows().get(0);

        assertThat(row.assignmentLabel()).isNull();
        assertThat(row.kindLabel()).isNull();
        assertThat(row.statusLabel()).isEqualTo("Presente");
    }
}
