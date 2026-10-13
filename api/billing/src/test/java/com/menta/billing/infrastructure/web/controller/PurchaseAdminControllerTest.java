package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.ExceptionPurchaseItem;
import com.menta.billing.application.dto.ExceptionPurchasePage;
import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;
import com.menta.billing.application.port.in.ResolveExceptionPurchaseUseCase;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PurchaseNotFoundException;
import com.menta.billing.domain.exception.PurchaseResolutionConflictException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PurchaseResolutionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc coverage for {@code GET /api/v1/admin/billing/purchases?status=EXCEPTION}
 * (#237, design C6/C7) — the exact {@code PaymentAdminControllerTest} setup: standalone {@code
 * MockMvc} plus {@link PaymentExceptionHandler} advice, so {@code
 * SecurityConfig}'s real filter chain is not exercised here (see {@code
 * PurchaseAdminExceptionIntegrationTest} for that, #237 Phase 3).
 */
class PurchaseAdminControllerTest {

    private static final String RESOLUTION_URL = "/api/v1/admin/billing/purchases/{purchaseId}/resolution";

    private PurchaseRepository purchaseRepository;
    private ResolveExceptionPurchaseUseCase resolveUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(PurchaseRepository.class);
        resolveUseCase = mock(ResolveExceptionPurchaseUseCase.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PurchaseAdminController(purchaseRepository, resolveUseCase))
            .setControllerAdvice(new PaymentExceptionHandler())
            .build();
    }

    private static Authentication authOf(String role) {
        return new UsernamePasswordAuthenticationToken(
            UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static ExceptionPurchaseItem exceptionItem() {
        return new ExceptionPurchaseItem(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PHYSICAL", "quote-1",
            new BigDecimal("100.00"), "ARS", Instant.parse("2026-01-01T10:00:00Z"), List.of("session-1")
        );
    }

    /** Deviation from #33's precedent: the spec requires an explicit 400, not a clamp to 50. */
    @Test
    void list_with_page_size_above_50_returns_400_and_no_results() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/purchases")
            .param("status", "EXCEPTION")
            .param("size", "51")
            .principal(authOf("ADMIN")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void list_defaults_to_page_zero_size_twenty_when_size_is_absent() throws Exception {
        when(purchaseRepository.findInException(eq(0), eq(20)))
            .thenReturn(new ExceptionPurchasePage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/billing/purchases")
            .param("status", "EXCEPTION")
            .principal(authOf("ADMIN")))
            .andExpect(status().isOk());

        verify(purchaseRepository).findInException(0, 20);
    }

    @Test
    void list_from_a_non_admin_returns_403_and_never_calls_the_repository() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/purchases")
            .param("status", "EXCEPTION")
            .principal(authOf("STUDENT")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void list_returns_the_page_from_the_repository_mapping_every_field() throws Exception {
        ExceptionPurchaseItem item = exceptionItem();
        ExceptionPurchasePage page = new ExceptionPurchasePage(List.of(item), 0, 20, 1, 1);
        when(purchaseRepository.findInException(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/billing/purchases")
            .param("status", "EXCEPTION")
            .principal(authOf("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].purchaseId").value(item.purchaseId().toString()))
            .andExpect(jsonPath("$.items[0].paymentId").value(item.paymentId().toString()))
            .andExpect(jsonPath("$.items[0].userId").value(item.userId().toString()))
            .andExpect(jsonPath("$.items[0].targetModality").value("PHYSICAL"))
            .andExpect(jsonPath("$.items[0].targetReference").value("quote-1"))
            .andExpect(jsonPath("$.items[0].currency").value("ARS"))
            .andExpect(jsonPath("$.items[0].physicalSessionIds[0]").value("session-1"));
    }

    @Test
    void list_maps_a_zero_session_row_with_an_empty_array() throws Exception {
        ExceptionPurchaseItem zeroSessionItem = new ExceptionPurchaseItem(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PHYSICAL", "quote-2",
            new BigDecimal("50.00"), "ARS", Instant.parse("2026-01-02T10:00:00Z"), List.of()
        );
        when(purchaseRepository.findInException(0, 20))
            .thenReturn(new ExceptionPurchasePage(List.of(zeroSessionItem), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/admin/billing/purchases")
            .param("status", "EXCEPTION")
            .principal(authOf("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].physicalSessionIds").isArray())
            .andExpect(jsonPath("$.items[0].physicalSessionIds").isEmpty());
    }

    // --- POST /{purchaseId}/resolution (#539) ----------------------------------

    private static Authentication adminAuth(UUID adminId) {
        return new UsernamePasswordAuthenticationToken(
            adminId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private static String body(String resolution, String note) {
        String resolutionJson = resolution == null ? "null" : "\"" + resolution + "\"";
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return "{\"resolution\":" + resolutionJson + ",\"note\":" + noteJson + "}";
    }

    private ResultActions postResolution(
        UUID purchaseId, String json, Authentication authentication
    ) throws Exception {
        return mockMvc.perform(post(RESOLUTION_URL, purchaseId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .principal(authentication));
    }

    @Test
    void resolve_returns_200_with_the_resolution_record_and_passes_the_admin_id_to_the_use_case()
        throws Exception {
        UUID purchaseId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Instant resolvedAt = Instant.parse("2026-10-13T12:00:00Z");
        when(resolveUseCase.resolve(any())).thenReturn(new PurchaseResolutionResult(
            purchaseId, paymentId, "RESOLVED", "REFUNDED", "Reembolsado por MercadoPago", adminId, resolvedAt,
            List.of("session-1")
        ));

        postResolution(purchaseId, body("REFUNDED", "Reembolsado por MercadoPago"), adminAuth(adminId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchaseId").value(purchaseId.toString()))
            .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.resolution").value("REFUNDED"))
            .andExpect(jsonPath("$.note").value("Reembolsado por MercadoPago"))
            .andExpect(jsonPath("$.resolvedBy").value(adminId.toString()))
            .andExpect(jsonPath("$.resolvedAt").exists())
            .andExpect(jsonPath("$.physicalSessionIds[0]").value("session-1"));

        ArgumentCaptor<ResolveExceptionPurchaseCommand> captor =
            ArgumentCaptor.forClass(ResolveExceptionPurchaseCommand.class);
        verify(resolveUseCase).resolve(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ResolveExceptionPurchaseCommand(
            purchaseId, PurchaseResolutionType.REFUNDED, "Reembolsado por MercadoPago", adminId
        ));
    }

    @Test
    void resolve_from_a_non_admin_returns_403_and_never_calls_the_use_case() throws Exception {
        postResolution(UUID.randomUUID(), body("COMPENSATED", "nota"), authOf("STUDENT"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(resolveUseCase);
    }

    @Test
    void resolve_rejects_a_blank_note_with_400_and_never_calls_the_use_case() throws Exception {
        postResolution(UUID.randomUUID(), body("REFUNDED", "   "), authOf("ADMIN"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolveUseCase);
    }

    @Test
    void resolve_rejects_a_note_over_500_characters_with_400() throws Exception {
        postResolution(UUID.randomUUID(), body("REFUNDED", "x".repeat(501)), authOf("ADMIN"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolveUseCase);
    }

    @Test
    void resolve_rejects_a_missing_resolution_with_400() throws Exception {
        postResolution(UUID.randomUUID(), body(null, "nota"), authOf("ADMIN"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolveUseCase);
    }

    @Test
    void resolve_rejects_an_unknown_resolution_value_with_400() throws Exception {
        postResolution(UUID.randomUUID(), body("DELETED", "nota"), authOf("ADMIN"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolveUseCase);
    }

    @Test
    void resolve_maps_an_unknown_purchase_to_404_problem_details() throws Exception {
        UUID purchaseId = UUID.randomUUID();
        when(resolveUseCase.resolve(any()))
            .thenThrow(new PurchaseNotFoundException(purchaseId));

        postResolution(purchaseId, body("REFUNDED", "nota"), authOf("ADMIN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    void resolve_maps_a_purchase_not_in_exception_to_409_problem_details() throws Exception {
        when(resolveUseCase.resolve(any())).thenThrow(
            new IllegalPurchaseStateTransitionException(
                PaymentId.generate(), FulfillmentStatus.ASSIGNED, FulfillmentStatus.RESOLVED
            ));

        postResolution(UUID.randomUUID(), body("REFUNDED", "nota"), authOf("ADMIN"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ILLEGAL_PURCHASE_STATE_TRANSITION"));
    }

    @Test
    void resolve_maps_a_conflicting_resolution_to_409_problem_details() throws Exception {
        UUID purchaseId = UUID.randomUUID();
        when(resolveUseCase.resolve(any()))
            .thenThrow(new PurchaseResolutionConflictException(purchaseId, PurchaseResolutionType.REFUNDED));

        postResolution(purchaseId, body("COMPENSATED", "otra nota"), authOf("ADMIN"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PURCHASE_ALREADY_RESOLVED"));
    }

    @Test
    void resolve_rejects_a_malformed_purchase_id_with_400() throws Exception {
        mockMvc.perform(post(RESOLUTION_URL, "not-a-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("REFUNDED", "nota"))
            .principal(authOf("ADMIN")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(resolveUseCase);
    }
}
