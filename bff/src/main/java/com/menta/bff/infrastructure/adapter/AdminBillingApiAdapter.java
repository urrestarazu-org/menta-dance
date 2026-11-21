package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CorrectionDecision;
import com.menta.bff.application.dto.PaymentDetail;
import com.menta.bff.application.dto.PaymentModality;
import com.menta.bff.application.dto.PaymentProof;
import com.menta.bff.application.dto.PaymentStatusType;
import com.menta.bff.application.dto.PendingPayment;
import com.menta.bff.application.dto.PendingPaymentPage;
import com.menta.bff.application.port.out.AdminBillingApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the administrator's manual payment verification (#370): the calls of the Billing API
 * under {@code /api/v1/admin/billing/payments}, as {@code billing-v1.yaml} describes them.
 *
 * <p>The calls send {@code Authorization: Bearer <token>}. The token only ever travels in that header: it is
 * never put in a log line or an exception message, and neither are identifiers, reasons, evidence or any
 * response. A refusal with one of the expected codes (400, 404 or 409) becomes a
 * {@link AdminBillingApiClient.PaymentRefusalException}; every other outcome (401, 403, 429, 5xx, an unexpected
 * status or code, an unreadable or incomplete body, a transport error, a timeout) becomes a
 * {@link AdminBillingApiClient.ServiceUnavailableException}, as the other authenticated adapters do: the API
 * makes the real authorization decision.
 * </p>
 *
 * <p>The only field read from an error body is its {@code code}; the body of a successful write is released
 * unread. The signed {@code proofUrl} of the detail is reduced to {@code hasProof} here, so it never leaves the
 * adapter.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit constructor because
 * {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class AdminBillingApiAdapter implements AdminBillingApiClient {

    private static final String PAYMENTS_ENDPOINT = "/api/v1/admin/billing/payments";
    private static final String PAYMENT_ENDPOINT = PAYMENTS_ENDPOINT + "/{paymentId}";
    private static final String APPROVE_ENDPOINT = PAYMENT_ENDPOINT + "/approve";
    private static final String REJECT_ENDPOINT = PAYMENT_ENDPOINT + "/reject";
    private static final String CORRECTIONS_ENDPOINT = PAYMENT_ENDPOINT + "/corrections";
    private static final Set<Integer> REFUSAL_STATUSES = Set.of(400, 404, 409);
    /** The proof types the API accepts on upload: the only ones the BFF hands to a browser. */
    private static final Set<String> PROOF_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");
    /** A signed token is two base64url halves joined by a dot: nothing in it needs escaping in a URI. */
    private static final String PROOF_TOKEN = "[A-Za-z0-9_.-]+";

    private final WebClient webClient;
    private final BillingApiProperties properties;

    public AdminBillingApiAdapter(
            @Qualifier("billingApiWebClient") WebClient webClient, BillingApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public PendingPaymentPage listPendingPayments(String accessToken, int page, int size) {
        WebClient.RequestHeadersSpec<?> request = webClient.get().uri(uriBuilder -> uriBuilder
                .path(PAYMENTS_ENDPOINT)
                .queryParam("status", "PENDING")
                .queryParam("substatus", "AWAITING_MANUAL_VERIFICATION")
                .queryParam("page", "{page}")
                .queryParam("size", "{size}")
                .build(page, size));
        return toDomain(call("list pending payments", request, accessToken, PendingPageWire.class));
    }

    @Override
    public PaymentDetail getPaymentDetail(String accessToken, String paymentId) {
        return toDomain(readDetail(accessToken, paymentId));
    }

    private DetailWire readDetail(String accessToken, String paymentId) {
        return call("get payment detail", webClient.get().uri(PAYMENT_ENDPOINT, paymentId), accessToken,
                DetailWire.class);
    }

    @Override
    public PaymentProof fetchPaymentProof(String accessToken, String paymentId) {
        String proofUrl = readDetail(accessToken, paymentId).proofUrl();
        if (proofUrl == null) {
            throw new PaymentRefusalException(PaymentRefusalException.Code.PROOF_NOT_FOUND);
        }
        // Anti-SSRF: the link is only ever followed when it is exactly the relative one the API issues for this
        // payment. It is a literal path made of unreserved characters, so it reaches the URI builder unescaped.
        if (!Pattern.matches(Pattern.quote(proofPath(paymentId)) + "\\?token=" + PROOF_TOKEN, proofUrl)) {
            throw unavailable("payment proof link is not the expected one");
        }
        // No Authorization header on purpose: the signed token in the query is this endpoint's credential.
        PaymentProof proof = await("get payment proof",
                webClient.get().uri(proofUrl).exchangeToMono(AdminBillingApiAdapter::readProof));
        if (proof == null) {
            throw unavailable("get payment proof answered without a body");
        }
        return proof;
    }

    @Override
    public void approvePayment(String accessToken, String paymentId) {
        call("approve payment", webClient.post().uri(APPROVE_ENDPOINT, paymentId), accessToken, Void.class);
    }

    @Override
    public void rejectPayment(String accessToken, String paymentId, String reason) {
        call("reject payment", webClient.post().uri(REJECT_ENDPOINT, paymentId).bodyValue(new RejectWire(reason)),
                accessToken, Void.class);
    }

    @Override
    public void correctPayment(
            String accessToken, String paymentId, CorrectionDecision decision, String reason, String evidence) {
        CorrectionWire body = new CorrectionWire(decision.name(), reason, evidence);
        call("correct payment", webClient.post().uri(CORRECTIONS_ENDPOINT, paymentId).bodyValue(body), accessToken,
                Void.class);
    }

    /**
     * Sends the request with the bearer token and reads the answer: a 200 is the success, the expected refusals
     * are typed, everything else is unavailable. With {@code Void} as wire type the body is not read and the
     * result is {@code null}; with any other type a missing body is unavailable.
     */
    private <W> W call(String operation, WebClient.RequestHeadersSpec<?> request, String accessToken,
            Class<W> wireType) {
        W wire = await(operation, request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchangeToMono(response -> read(response, wireType)));
        if (wire == null && wireType != Void.class) {
            throw unavailable(operation + " answered without a body");
        }
        return wire;
    }

    /** Waits for the answer within the configured timeout; anything unexpected becomes unavailable. */
    private <T> T await(String operation, Mono<T> answer) {
        try {
            return answer.timeout(properties.getTimeout()).block();
        } catch (PaymentRefusalException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) or the response may reach a log.
            log.warn("Billing API {} call failed: {}", operation, e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Billing API unavailable", e);
        }
    }

    /** Only a 200 is success; a refusal needs its code, any other error body is released unread. */
    private static <W> Mono<W> read(ClientResponse response, Class<W> wireType) {
        int status = response.statusCode().value();
        if (status == 200) {
            return response.bodyToMono(wireType);
        }
        if (REFUSAL_STATUSES.contains(status)) {
            return response.bodyToMono(ProblemWire.class)
                    .flatMap(problem -> Mono.<W>error(refusal(status, problem)))
                    .switchIfEmpty(Mono.error(unavailable("answered " + status + " without a body")));
        }
        return response.releaseBody().then(Mono.error(unavailable("answered " + status)));
    }

    /** Only a 200 whose declared type is an allowed one is a proof; anything else is released unread. */
    private static Mono<PaymentProof> readProof(ClientResponse response) {
        if (response.statusCode().value() != 200) {
            int status = response.statusCode().value();
            return response.releaseBody().then(Mono.error(unavailable("proof answered " + status)));
        }
        String type = response.headers().contentType()
                .map(mediaType -> mediaType.getType() + "/" + mediaType.getSubtype()).orElse("");
        if (!PROOF_CONTENT_TYPES.contains(type)) {
            return response.releaseBody().then(Mono.error(unavailable("proof has an unexpected content type")));
        }
        return response.bodyToMono(byte[].class).map(content -> new PaymentProof(content, type));
    }

    private static String proofPath(String paymentId) {
        return "/api/v1/billing/payments/" + paymentId + "/proof";
    }

    private static RuntimeException refusal(int status, ProblemWire problem) {
        for (PaymentRefusalException.Code code : PaymentRefusalException.Code.values()) {
            // PROOF_NOT_FOUND is the BFF's own code, from a detail without proofUrl: the API never answers it.
            if (code != PaymentRefusalException.Code.PROOF_NOT_FOUND && code.name().equals(problem.code())) {
                return new PaymentRefusalException(code);
            }
        }
        return unavailable("answered " + status);
    }

    private static PendingPaymentPage toDomain(PendingPageWire wire) {
        if (wire.items() == null || wire.page() == null || wire.size() == null || wire.totalElements() == null
                || wire.totalPages() == null) {
            throw unavailable("pending payments page is incomplete");
        }
        List<PendingPayment> items = wire.items().stream().map(AdminBillingApiAdapter::toDomain).toList();
        return new PendingPaymentPage(items, wire.page(), wire.size(), wire.totalElements(), wire.totalPages());
    }

    private static PendingPayment toDomain(PendingItemWire item) {
        if (item == null || item.paymentId() == null || item.userId() == null || item.targetModality() == null
                || item.targetReference() == null || item.amount() == null || item.currency() == null
                || item.statusType() == null || item.hasProof() == null || item.createdAt() == null) {
            throw unavailable("pending payment is incomplete");
        }
        return new PendingPayment(item.paymentId(), item.userId(),
                ApiEnums.parse(PaymentModality.class, item.targetModality()), item.targetReference(), item.amount(),
                item.currency(), ApiEnums.parse(PaymentStatusType.class, item.statusType()), item.hasProof(),
                item.createdAt());
    }

    private static PaymentDetail toDomain(DetailWire wire) {
        if (wire.paymentId() == null || wire.userId() == null || wire.targetModality() == null
                || wire.targetReference() == null || wire.amount() == null || wire.currency() == null
                || wire.statusType() == null || wire.createdAt() == null) {
            throw unavailable("payment detail is incomplete");
        }
        // The signed URL stops here: only whether there is a proof goes on.
        return new PaymentDetail(wire.paymentId(), wire.userId(),
                ApiEnums.parse(PaymentModality.class, wire.targetModality()), wire.targetReference(), wire.amount(),
                wire.currency(), ApiEnums.parse(PaymentStatusType.class, wire.statusType()), wire.createdAt(),
                wire.proofUrl() != null);
    }

    private static ServiceUnavailableException unavailable(String reason) {
        return new ServiceUnavailableException("Billing API unavailable: " + reason);
    }

    /** {@code PendingVerificationPageResponse}. */
    private record PendingPageWire(
            List<PendingItemWire> items, Integer page, Integer size, Long totalElements, Integer totalPages) {
    }

    /** {@code PendingVerificationItemResponse}. */
    private record PendingItemWire(
            String paymentId, String userId, String targetModality, String targetReference, BigDecimal amount,
            String currency, String statusType, Boolean hasProof, Instant createdAt) {
    }

    /** {@code PaymentDetailResponse}; {@code proofUrl} is checked for presence or, for the proof, followed. */
    private record DetailWire(
            String paymentId, String userId, String targetModality, String targetReference, BigDecimal amount,
            String currency, String statusType, Instant createdAt, String proofUrl) {
    }

    /** {@code RejectPaymentRequest}. */
    private record RejectWire(String reason) {
    }

    /** {@code CorrectPaymentRequest}. */
    private record CorrectionWire(String decision, String reason, String evidence) {
    }

    /** The only field read from an error body. */
    private record ProblemWire(String code) {
    }
}
