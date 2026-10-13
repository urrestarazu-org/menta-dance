package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.AssignedSession;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
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
@DisplayName("GetAssignedSessionsUseCase")
class GetAssignedSessionsUseCaseImplTest {

    private static final ZoneId ACADEMY_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String TOKEN = "access-token-abc";

    @Mock
    private PhysicalSessionsApiClient physicalSessionsApiClient;

    private GetAssignedSessionsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAssignedSessionsUseCaseImpl(physicalSessionsApiClient, ACADEMY_ZONE);
    }

    @Test
    @DisplayName("words each session in the academy zone, soonest first")
    void wordsSessionsSoonestFirst() {
        when(physicalSessionsApiClient.findMySessions(TOKEN)).thenReturn(List.of(
                new AssignedSession("s-2", "Bachata", Instant.parse("2026-10-21T01:30:00Z")),
                new AssignedSession("s-1", "Salsa en vivo", Instant.parse("2026-10-19T21:00:00Z"))));

        List<AssignedSessionRow> rows = useCase.execute(TOKEN);

        assertThat(rows).extracting(AssignedSessionRow::sessionId).containsExactly("s-1", "s-2");
        assertThat(rows).extracting(AssignedSessionRow::courseName).containsExactly("Salsa en vivo", "Bachata");
        assertThat(rows).extracting(AssignedSessionRow::momentLabel)
                .containsExactly("lunes 19 de octubre a las 18:00", "martes 20 de octubre a las 22:30");
    }

    @Test
    @DisplayName("a session without a class name keeps a neutral label")
    void blankCourseName() {
        when(physicalSessionsApiClient.findMySessions(TOKEN)).thenReturn(List.of(
                new AssignedSession("s-1", null, Instant.parse("2026-10-19T21:00:00Z"))));

        assertThat(useCase.execute(TOKEN)).extracting(AssignedSessionRow::courseName).containsExactly("Clase");
    }

    @Test
    @DisplayName("an unavailable lookup degrades to an empty list")
    void degradesToEmpty() {
        when(physicalSessionsApiClient.findMySessions(TOKEN))
                .thenThrow(new PhysicalSessionsApiClient.ServiceUnavailableException("down"));

        assertThat(useCase.execute(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("without an access token nothing is requested")
    void withoutToken() {
        assertThat(useCase.execute(null)).isEmpty();
        assertThat(useCase.execute(" ")).isEmpty();

        verifyNoInteractions(physicalSessionsApiClient);
    }
}
