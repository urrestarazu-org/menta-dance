package com.menta.billing.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.billing.application.dto.ExceptionPurchaseItem;
import com.menta.billing.application.dto.ExceptionPurchasePage;
import com.menta.billing.application.port.out.PurchaseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc coverage for {@code GET /api/v1/admin/billing/purchases?status=EXCEPTION}
 * (#237, design C6/C7) — the exact {@code PaymentAdminControllerTest} setup: standalone {@code
 * MockMvc} plus {@link PaymentExceptionHandler} advice, so {@code
 * SecurityConfig}'s real filter chain is not exercised here (see {@code
 * PurchaseAdminExceptionIntegrationTest} for that, #237 Phase 3).
 */
class PurchaseAdminControllerTest {

    private PurchaseRepository purchaseRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(PurchaseRepository.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PurchaseAdminController(purchaseRepository))
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
}
