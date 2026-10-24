package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The contact details come from configuration (#650), never from the repository: with them set, the
 * contact page shows them as links; every value here is a fake one.
 */
@DisplayName("Contact page with configured details Integration Tests")
@TestPropertySource(properties = {
    "menta.contact.email=hola@example.test",
    "menta.contact.phone=+00 0 000 000-0000",
    "menta.contact.address=Calle Falsa 123, Ciudad Ejemplo"
})
class LegalContactConfiguredIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("muestra el email, el teléfono y el domicilio configurados")
    void showsTheConfiguredDetails() throws Exception {
        String main = HtmlSections.mainOf(mockMvc.perform(get("/contacto")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(main).contains("href=\"mailto:hola@example.test\"");
        assertThat(main).contains("href=\"tel:+0000000000000\"");
        assertThat(main).contains("Calle Falsa 123, Ciudad Ejemplo");
        assertThat(main).doesNotContain("Datos de contacto pendientes");
    }
}
