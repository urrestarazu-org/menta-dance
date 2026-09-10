package com.menta.bff.infrastructure.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack rendering coverage for the plans-listing view (spec {@code
 * bff-plans-view}), asserting on actual rendered content against a
 * WireMock-stubbed billing upstream — distinct from
 * {@link VirtualLearningSecurityIntegrationTest}, which only asserts on
 * reachability/redirection for {@code GET /plans}.
 */
@DisplayName("Billing Plans View Integration Tests")
class BillingPlansViewIntegrationTest extends BaseIntegrationTest {

    private static final String PLANS_URL = "/api/v1/billing/plans";

    /**
     * The featured plan ("Plan Anual") is deliberately NOT first in the
     * response order — this is the load-bearing proof of D5: the badge must
     * appear without moving the plan to the front of the rendered list.
     */
    private static final String PLANS_BODY = """
            {
              "plans": [
                {
                  "id": "plan-1",
                  "name": "Plan Mensual",
                  "description": "Acceso completo por un mes",
                  "price": 4999.00,
                  "currency": "ARS",
                  "durationDays": 30,
                  "featured": false
                },
                {
                  "id": "plan-2",
                  "name": "Plan Anual",
                  "description": "Acceso completo por un año, con descuento",
                  "price": 49990.00,
                  "currency": "ARS",
                  "durationDays": 365,
                  "featured": true
                }
              ]
            }
            """;

    private void stubPlans() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PLANS_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PLANS_BODY)));
    }

    @Test
    @DisplayName("renders each plan's name, description, price+currency, and duration")
    void plansRequest_shouldRenderNameDescriptionPriceCurrencyAndDuration() throws Exception {
        stubPlans();

        var result = mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Plan Mensual");
        assertThat(body).contains("Acceso completo por un mes");
        assertThat(body).contains("4.999,00");
        assertThat(body).contains("ARS");
        assertThat(body).contains("cada 30 días");
        assertThat(body).contains("Plan Anual");
        assertThat(body).contains("Acceso completo por un año, con descuento");
        assertThat(body).contains("49.990,00");
        assertThat(body).contains("cada 365 días");
    }

    @Test
    @DisplayName("a featured plan not first in response order shows the badge without reordering the list")
    void featuredPlanNotFirst_shouldShowBadgeWithoutReordering() throws Exception {
        stubPlans();

        var result = mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Destacado");
        // The list order must follow the upstream response order exactly
        // (D5): "Plan Mensual" (non-featured, first in the stub) still
        // appears before "Plan Anual" (featured, second in the stub).
        int mensualIndex = body.indexOf("Plan Mensual");
        int anualIndex = body.indexOf("Plan Anual");
        assertThat(mensualIndex).isGreaterThanOrEqualTo(0);
        assertThat(anualIndex).isGreaterThan(mensualIndex);
    }

    @Test
    @DisplayName("a non-featured plan shows no Destacado badge for it")
    void nonFeaturedPlan_shouldShowNoBadge() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PLANS_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "plans": [
                                    {
                                      "id": "plan-1",
                                      "name": "Plan Mensual",
                                      "description": "Acceso completo por un mes",
                                      "price": 4999.00,
                                      "currency": "ARS",
                                      "durationDays": 30,
                                      "featured": false
                                    }
                                  ]
                                }
                                """)));

        var result = mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Destacado");
    }

    @Test
    @DisplayName("billing 503 renders the shared error view with no leaked problem-detail body and no plans-specific copy")
    void billingUnavailable_shouldRenderErrorViewWithoutLeakingUpstreamDetails() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(PLANS_URL))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "30")
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {"type":"about:blank","title":"Service unavailable","status":503}
                                """)));

        var result = mockMvc.perform(get("/plans"))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("about:blank");
        assertThat(body).doesNotContain("Service unavailable");
        assertThat(body).doesNotContain("application/problem+json");
        assertThat(body).doesNotContain("Plan Mensual");
        assertThat(body).doesNotContain("Destacado");
    }
}
