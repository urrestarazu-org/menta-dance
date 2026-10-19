package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.AssignedSession;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient adapter for {@code GET /api/v1/physical/sessions/me} (#542).
 *
 * <p>It shares the api:app WebClient and timeout of the catalog adapters: the class page
 * is the only caller and it renders next to the catalog data. Every failure (transport, timeout,
 * non-2xx, unusable body) collapses into
 * {@link PhysicalSessionsApiClient.ServiceUnavailableException}, because the page degrades
 * identically for all of them. The access token only travels in the {@code Authorization}
 * header; only the exception class name is logged, never the request or the response.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit constructor because
 * {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class PhysicalSessionsApiAdapter implements PhysicalSessionsApiClient {

    private static final String MY_SESSIONS_ENDPOINT = "/api/v1/physical/sessions/me";

    private final WebClient webClient;
    private final VirtualApiProperties properties;

    public PhysicalSessionsApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient, VirtualApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Set<String> findMySessionIds(String accessToken) {
        return fetch(accessToken).stream()
                .map(SessionWire::sessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<AssignedSession> findMySessions(String accessToken) {
        return fetch(accessToken).stream()
                .filter(s -> s.sessionId() != null && s.scheduledAt() != null)
                .map(s -> new AssignedSession(s.sessionId(), s.courseName(), s.scheduledAt()))
                .toList();
    }

    private List<SessionWire> fetch(String accessToken) {
        MySessionsWire wire;
        try {
            wire = webClient.get()
                    .uri(MY_SESSIONS_ENDPOINT)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(MySessionsWire.class)
                    .timeout(properties.getTimeout())
                    .block();
        } catch (RuntimeException e) {
            log.warn("Physical sessions call failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Physical sessions API unavailable", e);
        }
        if (wire == null || wire.sessions() == null) {
            throw new ServiceUnavailableException("Physical sessions API returned an unusable body");
        }
        return wire.sessions();
    }

    /** Wire shape of the response: only the fields the BFF shows are read. */
    private record MySessionsWire(List<SessionWire> sessions) {
    }

    private record SessionWire(String sessionId, String courseName, Instant scheduledAt) {
    }
}
