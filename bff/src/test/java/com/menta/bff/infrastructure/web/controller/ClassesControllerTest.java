package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.ClassCheckout;
import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.dto.ClassQuote;
import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import com.menta.bff.application.usecase.ClassCard;
import com.menta.bff.application.usecase.ClassDetailView;
import com.menta.bff.application.usecase.ClassPurchaseView;
import com.menta.bff.application.usecase.ClassQuoteView;
import com.menta.bff.application.usecase.GetClassDetailViewUseCase;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
import com.menta.bff.application.usecase.PurchaseClassUseCase;
import com.menta.bff.application.usecase.QuoteClassUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Unit tests for {@link ClassesController}, standalone MockMvc with no
 * security context — route access is proven by the integration tier.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClassesController")
class ClassesControllerTest {

    private static final String TOKEN = "token";

    @Mock
    private GetClassesViewUseCase getClassesViewUseCase;

    @Mock
    private GetClassDetailViewUseCase getClassDetailViewUseCase;

    @Mock
    private QuoteClassUseCase quoteClassUseCase;

    @Mock
    private PurchaseClassUseCase purchaseClassUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // A no-op view avoids "/classes" → "classes" circular forwards; these
        // tests assert model and view names only, never template output.
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders.standaloneSetup(new ClassesController(
                        getClassesViewUseCase, getClassDetailViewUseCase, quoteClassUseCase, purchaseClassUseCase))
                .setViewResolvers((viewName, locale) -> viewName.startsWith("redirect:")
                        ? new RedirectView(viewName.substring("redirect:".length()))
                        : noOpView)
                .build();
    }

    @Test
    @DisplayName("GET /classes renders the class list")
    void classes() throws Exception {
        List<ClassCard> cards = List.of(new ClassCard("c-1", "Salsa", "Inicial", "María", "Martes 19:00", 20));
        when(getClassesViewUseCase.execute()).thenReturn(cards);

        mockMvc.perform(get("/classes"))
                .andExpect(status().isOk())
                .andExpect(view().name("classes"))
                .andExpect(model().attribute("classes", cards));
    }

    @Test
    @DisplayName("GET /classes/{id} flags an anonymous caller as not signed in")
    void classDetail_anonymous() throws Exception {
        ClassDetailView detail = new ClassDetailView("c-1", "Salsa", "Inicial", "María", "Martes 19:00", null,
                List.of());
        when(getClassDetailViewUseCase.execute("c-1")).thenReturn(detail);

        mockMvc.perform(get("/classes/c-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("class-detail"))
                .andExpect(model().attribute("course", detail))
                .andExpect(model().attribute("signedIn", false));
    }

    @Test
    @DisplayName("GET /classes/{id} flags a caller with an access token as signed in")
    void classDetail_signedIn() throws Exception {
        when(getClassDetailViewUseCase.execute("c-1")).thenReturn(
                new ClassDetailView("c-1", "Salsa", "Inicial", "María", "Martes 19:00", null, List.of()));

        mockMvc.perform(get("/classes/c-1").requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(model().attribute("signedIn", true));
    }

    @Test
    @DisplayName("GET /classes/{id} for an unknown course resolves to 404")
    void classDetail_notFound() throws Exception {
        when(getClassDetailViewUseCase.execute("nope"))
                .thenThrow(new PhysicalCatalogApiClient.NotFoundException("no"));

        mockMvc.perform(get("/classes/nope")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST quote renders a payable quote with the caller's token")
    void quote_payable() throws Exception {
        ClassQuote quote = new ClassQuote("q-1", "c-1", ClassPurchaseType.INDIVIDUAL, "s-1", BigDecimal.TEN, "ARS",
                true, Instant.parse("2026-10-04T16:00:00Z"));
        ClassQuoteView.Payable payable = new ClassQuoteView.Payable("c-1", quote, "4/10 13:00", "key-1");
        when(quoteClassUseCase.execute("c-1", ClassPurchaseType.INDIVIDUAL, "s-1", TOKEN)).thenReturn(payable);

        mockMvc.perform(post("/classes/c-1/quote")
                        .param("purchaseType", "INDIVIDUAL")
                        .param("sessionId", "s-1")
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("class-quote"))
                .andExpect(model().attribute("payable", payable))
                .andExpect(model().attribute("courseId", "c-1"));
    }

    @Test
    @DisplayName("POST quote renders sold-out and rejected outcomes")
    void quote_outcomes() throws Exception {
        when(quoteClassUseCase.execute("c-1", ClassPurchaseType.MONTHLY, null, TOKEN))
                .thenReturn(new ClassQuoteView.SoldOut("c-1"))
                .thenReturn(new ClassQuoteView.Rejected("c-1"));

        mockMvc.perform(post("/classes/c-1/quote").param("purchaseType", "MONTHLY")
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(model().attribute("outcome", "soldOut"));
        mockMvc.perform(post("/classes/c-1/quote").param("purchaseType", "MONTHLY")
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(model().attribute("outcome", "rejected"));
    }

    @Test
    @DisplayName("POST quote with an unknown purchase type is a 400")
    void quote_unknownType() throws Exception {
        mockMvc.perform(post("/classes/c-1/quote").param("purchaseType", "YEARLY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST purchase with Mercado Pago redirects to the provider")
    void purchase_redirects() throws Exception {
        when(purchaseClassUseCase.execute("c-1", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .thenReturn(new ClassPurchaseView.RedirectToProvider("https://mp.example/checkout"));

        mockMvc.perform(purchaseRequest("MERCADO_PAGO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://mp.example/checkout"));
    }

    @Test
    @DisplayName("POST purchase with bank transfer renders the instructions")
    void purchase_bankTransfer() throws Exception {
        ClassCheckout.BankTransfer instructions = new ClassCheckout.BankTransfer(
                "cbu", "alias", "holder", "cuit", BigDecimal.TEN, "ARS", "SUB-1");
        when(purchaseClassUseCase.execute("c-1", "q-1", ClassPaymentMethod.BANK_TRANSFER, "key-1", TOKEN))
                .thenReturn(new ClassPurchaseView.BankTransfer("c-1", instructions));

        mockMvc.perform(purchaseRequest("BANK_TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(view().name("class-purchase"))
                .andExpect(model().attribute("bankTransfer", instructions));
    }

    @Test
    @DisplayName("POST purchase renders sold-out and expired-quote outcomes")
    void purchase_outcomes() throws Exception {
        when(purchaseClassUseCase.execute("c-1", "q-1", ClassPaymentMethod.MERCADO_PAGO, "key-1", TOKEN))
                .thenReturn(new ClassPurchaseView.SoldOut("c-1"))
                .thenReturn(new ClassPurchaseView.QuoteExpired("c-1"));

        mockMvc.perform(purchaseRequest("MERCADO_PAGO"))
                .andExpect(model().attribute("outcome", "soldOut"))
                .andExpect(model().attribute("courseId", "c-1"));
        mockMvc.perform(purchaseRequest("MERCADO_PAGO"))
                .andExpect(model().attribute("outcome", "quoteExpired"));
    }

    private static RequestBuilder purchaseRequest(String paymentMethod) {
        return post("/classes/purchases")
                .param("courseId", "c-1")
                .param("quoteId", "q-1")
                .param("paymentMethod", paymentMethod)
                .param("idempotencyKey", "key-1")
                .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN);
    }
}
