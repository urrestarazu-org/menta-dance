package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.port.out.InstructorDisplayNameSource;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for {@code GET /api/v1/users/me} of the Auth API, the source of the signed-in instructor's
 * display name behind {@link InstructorDisplayNameSource} (#749).
 *
 * <p>The display name is the first and the last name, each stripped and joined by a space; a missing or blank
 * part is dropped, and an account with neither (no profile) has no display name.
 * </p>
 *
 * <p>Fails closed: any outcome that is not a 200 with a readable body (401, any other status, a transport error,
 * a timeout, a body that is not JSON) answers empty, which keeps the instructor from creating a course instead of
 * sending a made-up name. The token only ever travels in the {@code Authorization} header: the log lines carry the
 * status or the exception class, never the token nor the response body (names).
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit constructor because
 * {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class CurrentUserApiAdapter implements InstructorDisplayNameSource {

    private static final String ME_ENDPOINT = "/api/v1/users/me";

    private final WebClient webClient;
    private final AuthProperties properties;

    public CurrentUserApiAdapter(@Qualifier("webClient") WebClient webClient, AuthProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Optional<String> displayNameOf(String accessToken) {
        CurrentUserWire wire;
        try {
            wire = webClient.get()
                    .uri(ME_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(CurrentUserApiAdapter::read)
                    .timeout(properties.getTimeout())
                    .block();
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers) or the response may reach a log.
            log.warn("Auth API current user call failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
        return wire == null ? Optional.empty() : joinNames(wire);
    }

    /** Only a 200 is read; any other answer is logged by its status and its body released unread. */
    private static Mono<CurrentUserWire> read(ClientResponse response) {
        int status = response.statusCode().value();
        if (status == HttpStatus.OK.value()) {
            return response.bodyToMono(CurrentUserWire.class);
        }
        log.warn("Auth API current user call answered {}", status);
        return response.releaseBody().then(Mono.empty());
    }

    private static Optional<String> joinNames(CurrentUserWire wire) {
        String name = Stream.of(wire.firstName(), wire.lastName())
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    /** Wire shape of the response: only the names are read. */
    private record CurrentUserWire(String firstName, String lastName) {
    }
}
