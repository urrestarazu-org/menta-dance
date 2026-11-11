package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserStatus;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for {@code GET /api/v1/admin/users} of the Auth API.
 *
 * <p>The call sends {@code Authorization: Bearer <token>}. The token only ever travels in that header:
 * it is never put in a log line or an exception message, and neither are the request parameters (search
 * text typed by the administrator) or the response (emails and names). A 400 becomes an
 * {@link AdminUsersApiClient.InvalidRequestException}; every other outcome that is not a usable page (401,
 * 403, 429, 5xx, an unexpected status, an unreadable or incomplete body, a transport error, a timeout)
 * becomes an {@link AdminUsersApiClient.ServiceUnavailableException}, as the other authenticated adapters
 * do: the page degrades the same way for all of them, and the API makes the real authorization decision.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit constructor because
 * {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class AdminUsersApiAdapter implements AdminUsersApiClient {

    private static final String USERS_ENDPOINT = "/api/v1/admin/users";

    private final WebClient webClient;
    private final AuthProperties properties;

    public AdminUsersApiAdapter(@Qualifier("webClient") WebClient webClient, AuthProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public UserPage listUsers(String accessToken, UserListQuery query) {
        try {
            UserPageWire wire = webClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, query))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(this::read)
                    .timeout(properties.getTimeout())
                    .block();
            return toDomain(wire);
        } catch (InvalidRequestException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, parameters) or the response may reach a log.
            log.warn("Auth API users list call failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Auth API unavailable", e);
        }
    }

    /**
     * Builds the query string with URI variables, which are encoded strictly: a literal query parameter
     * would let {@code +}, {@code &} or {@code =} typed in the search box change what the API receives.
     */
    private static URI buildUri(UriBuilder uriBuilder, UserListQuery query) {
        Map<String, Object> variables = new HashMap<>();
        uriBuilder.path(USERS_ENDPOINT).queryParam("page", "{page}").queryParam("size", "{size}");
        variables.put("page", query.page());
        variables.put("size", query.size());
        if (query.role() != null) {
            uriBuilder.queryParam("role", "{role}");
            variables.put("role", query.role().name());
        }
        if (query.text() != null) {
            uriBuilder.queryParam("q", "{q}");
            variables.put("q", query.text());
        }
        return uriBuilder.build(variables);
    }

    private Mono<UserPageWire> read(ClientResponse response) {
        int status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(UserPageWire.class);
        }
        // The error body is never read: only the status decides, and nothing of it may travel on.
        return response.releaseBody().then(Mono.error(status == 400
                ? new InvalidRequestException("Auth API rejected the users query")
                : unavailable("answered " + status)));
    }

    private static UserPage toDomain(UserPageWire wire) {
        if (wire == null || wire.items() == null || wire.page() == null || wire.size() == null
                || wire.totalElements() == null || wire.totalPages() == null) {
            throw unavailable("users page is incomplete");
        }
        List<UserSummary> items = wire.items().stream().map(AdminUsersApiAdapter::toDomain).toList();
        return new UserPage(items, wire.page(), wire.size(), wire.totalElements(), wire.totalPages());
    }

    private static UserSummary toDomain(UserWire user) {
        if (user == null || user.id() == null || user.email() == null || user.role() == null
                || user.status() == null || user.createdAt() == null) {
            throw unavailable("user item is incomplete");
        }
        return new UserSummary(user.id(), user.email(), user.firstName(), user.lastName(), user.role(),
                user.status(), user.createdAt());
    }

    private static ServiceUnavailableException unavailable(String reason) {
        return new ServiceUnavailableException("Auth API unavailable: " + reason);
    }

    /** Wire shape of the response: only the fields the BFF shows are read. */
    private record UserPageWire(
            List<UserWire> items, Integer page, Integer size, Long totalElements, Integer totalPages) {
    }

    private record UserWire(
            String id,
            String email,
            String firstName,
            String lastName,
            UserRole role,
            UserStatus status,
            LocalDateTime createdAt) {
    }
}
