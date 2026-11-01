package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.menta.bff.application.usecase.GetMembershipLandingViewUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** With the temporary landing off (the default), {@code /} is the regular catalog home (#685). */
@DisplayName("Membership landing disabled Integration Tests")
class MembershipLandingDisabledIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("/ renders the catalog home and the landing use case does not exist")
    void homeIsTheCatalog() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/courses"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody("{\"courses\": []}")));

        String html = mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Aprendé a tu ritmo").doesNotContain("Full Pass");
        assertThat(context.getBeanNamesForType(GetMembershipLandingViewUseCase.class)).isEmpty();
        WIRE_MOCK_SERVER.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/catalog/courses")));
    }
}
