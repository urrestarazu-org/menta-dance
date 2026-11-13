package com.menta.app.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.in.ResolveUserDisplayNamesUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The adapter only bridges the shared port to auth's application use case, in one call. */
class UserDisplayNamePortAdapterTest {

    private final ResolveUserDisplayNamesUseCase resolveUserDisplayNames = mock(ResolveUserDisplayNamesUseCase.class);
    private final UserDisplayNamePortAdapter adapter = new UserDisplayNamePortAdapter(resolveUserDisplayNames);

    @Test
    void resolves_every_requested_user_with_a_single_call_to_the_auth_use_case() {
        UUID ana = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        List<UUID> ids = List.of(ana, bruno);
        when(resolveUserDisplayNames.resolve(ids)).thenReturn(Map.of(ana, "Ana Perez", bruno, "Bruno Diaz"));

        Map<UUID, String> names = adapter.displayNamesOf(ids);

        assertThat(names).containsOnly(Map.entry(ana, "Ana Perez"), Map.entry(bruno, "Bruno Diaz"));
        verify(resolveUserDisplayNames).resolve(ids);
        verifyNoMoreInteractions(resolveUserDisplayNames);
    }

    @Test
    void a_user_the_auth_use_case_does_not_know_is_absent() {
        UUID unknown = UUID.randomUUID();
        when(resolveUserDisplayNames.resolve(List.of(unknown))).thenReturn(Map.of());

        assertThat(adapter.displayNamesOf(List.of(unknown))).isEmpty();
    }
}
