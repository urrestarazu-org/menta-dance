package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.List;

/**
 * WebClient adapter for the upstream billing plans endpoint.
 * <p>
 * Implements {@link BillingApiClient} using Spring WebClient to call
 * api:billing's public plans endpoint. This PR covers the fetch and
 * status-mapping behavior only — no caching yet, every call reaches
 * upstream (the single-entry TTL cache is added on top in a later PR).
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer. Uses an explicit
 * constructor because {@code @Qualifier("billingApiWebClient")} cannot be
 * carried by Lombok's {@code @RequiredArgsConstructor}, mirroring
 * {@code VirtualApiAdapter}.
 * </p>
 */
@Slf4j
@Component
public class BillingApiAdapter implements BillingApiClient {

    private static final String PLANS_ENDPOINT = "/api/v1/billing/plans";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final WebClient webClient;
    private final BillingApiProperties billingApiProperties;

    public BillingApiAdapter(
            @Qualifier("billingApiWebClient") WebClient webClient,
            BillingApiProperties billingApiProperties) {
        this.webClient = webClient;
        this.billingApiProperties = billingApiProperties;
    }

    @Override
    public List<PlanSummary> getPlans() {
        log.debug("Calling Billing API plans endpoint");

        try {
            ClientResponse response = webClient.get()
                    .uri(PLANS_ENDPOINT)
                    .exchange()
                    .timeout(billingApiProperties.getTimeout())
                    .block();

            return handlePlansResponse(response);

        } catch (NotFoundException | ServiceUnavailableException e) {
            // Re-throw domain exceptions without wrapping
            throw e;
        } catch (WebClientResponseException e) {
            throw mapHttpException(e);
        } catch (Exception e) {
            log.error("Billing API plans call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Billing API unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Handles the plans response, mapping success/error statuses. The
     * upstream JSON wraps the list under a {@code plans} key; {@link
     * PlanListWireResponse} binds that envelope and each {@link PlanWire}
     * is mapped to a trimmed {@link PlanSummary} (any {@code courses} field
     * the wire JSON carries is silently unmapped — {@code PlanSummary} has
     * no field for it).
     */
    private List<PlanSummary> handlePlansResponse(ClientResponse response) {
        if (response == null) {
            throw new ServiceUnavailableException("Billing API returned null response");
        }

        HttpStatus status = (HttpStatus) response.statusCode();

        if (status.is2xxSuccessful()) {
            PlanListWireResponse wire = response.bodyToMono(PlanListWireResponse.class).block();
            if (wire == null || wire.plans() == null) {
                throw new ServiceUnavailableException("Billing API returned empty response body");
            }
            return wire.plans().stream().map(PlanWire::toPlanSummary).toList();
        }

        throw mapErrorStatus(response, status);
    }

    /**
     * Maps an error {@link ClientResponse} status to a domain exception,
     * mirroring {@code VirtualApiAdapter#mapErrorStatus}. {@code 429} and
     * {@code 5xx} both map to {@link ServiceUnavailableException} — no
     * dedicated rate-limited type, since the visitor-facing outcome is
     * identical either way (design decision).
     */
    private RuntimeException mapErrorStatus(ClientResponse response, HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException("Plans not found: " + status);
        }

        if (status.value() == 429 || status.is5xxServerError()) {
            Long retryAfter = parseRetryAfter(response.headers().asHttpHeaders().getFirst(RETRY_AFTER_HEADER));
            return new ServiceUnavailableException("Plans unavailable: " + status, retryAfter);
        }

        String errorBody = response.bodyToMono(String.class).block();
        log.warn("Billing API plans call returned unexpected status {}: {}", status, errorBody);
        return new RuntimeException("Billing API call failed with status " + status + ": " + errorBody);
    }

    /**
     * Maps a {@link WebClientResponseException} to a domain exception,
     * mirroring {@code VirtualApiAdapter#mapHttpException}.
     */
    private RuntimeException mapHttpException(WebClientResponseException e) {
        HttpStatus status = (HttpStatus) e.getStatusCode();

        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException("Plans not found: " + status, e);
        }

        if (status.value() == 429 || status.is5xxServerError()) {
            Long retryAfter = parseRetryAfter(e.getHeaders().getFirst(RETRY_AFTER_HEADER));
            return new ServiceUnavailableException("Plans unavailable: " + status, retryAfter);
        }

        return new RuntimeException("Billing API call failed: " + status, e);
    }

    private Long parseRetryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Wire shape for {@code GET /api/v1/billing/plans}'s {@code {"plans":
     * [...]}} envelope. Kept private to this adapter — only the trimmed
     * {@link PlanSummary} crosses the port boundary into {@code application}.
     */
    private record PlanListWireResponse(List<PlanWire> plans) {
    }

    /**
     * Wire shape for a single plan entry. May carry a {@code courses} field
     * upstream; it is intentionally left unbound here and therefore dropped
     * silently when mapped to {@link PlanSummary} (design decision: the
     * plans page renders nothing from it).
     */
    private record PlanWire(
            String id,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int durationDays,
            boolean featured) {

        private PlanSummary toPlanSummary() {
            return new PlanSummary(id, name, description, price, currency, durationDays, featured);
        }
    }
}
