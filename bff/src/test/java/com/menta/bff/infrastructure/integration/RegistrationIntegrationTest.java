package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Registration through the rendered {@code /registro} page. Field names are
 * derived from the HTML, never hard-coded, so a template/controller mismatch
 * breaks the build (the class of bug behind #490).
 */
@DisplayName("Registration Integration Tests")
class RegistrationIntegrationTest extends BaseIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final Pattern EMAIL_FIELD = Pattern.compile("<input[^>]*type=\"email\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern PASSWORD_FIELD =
            Pattern.compile("<input[^>]*type=\"password\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern PHONE_FIELD = Pattern.compile("<input[^>]*type=\"tel\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern BIRTH_DATE_FIELD =
            Pattern.compile("<input[^>]*type=\"date\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern BIRTH_DATE_INPUT = Pattern.compile("<input[^>]*type=\"date\"[^>]*>");

    private String registerPage() throws Exception {
        return mockMvc.perform(get("/registro"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String fieldName(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).withFailMessage("The rendered page has no input matching %s", pattern).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("the anonymous form carries the CSRF token and exposes no role field")
    void formHasCsrfAndNoRoleField() throws Exception {
        String html = registerPage();

        assertThat(html).contains("name=\"_csrf\"");
        assertThat(html.toLowerCase()).doesNotContain("role").doesNotContain("rol\"");
    }

    @Test
    @DisplayName("submitting the rendered form registers a STUDENT and redirects to the pending screen")
    void submittingTheRenderedFormRegistersAStudent() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(REGISTER_URL)).willReturn(aResponse().withStatus(202)));
        String html = registerPage();

        mockMvc.perform(post("/registro")
                        .param("firstName", "Ana")
                        .param("lastName", "Prueba")
                        .param(fieldName(EMAIL_FIELD, html), "ana@example.com")
                        .param(fieldName(PHONE_FIELD, html), "+54 9 11 0000-0000")
                        .param(fieldName(BIRTH_DATE_FIELD, html), "1990-05-17")
                        .param(fieldName(PASSWORD_FIELD, html), "Secreta123")
                        .param("passwordConfirmation", "Secreta123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro/pendiente"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(REGISTER_URL))
                .withRequestBody(matchingJsonPath("$.email", equalTo("ana@example.com")))
                .withRequestBody(matchingJsonPath("$.firstName", equalTo("Ana")))
                .withRequestBody(matchingJsonPath("$.lastName", equalTo("Prueba")))
                .withRequestBody(matchingJsonPath("$.phone", equalTo("+5491100000000")))
                .withRequestBody(matchingJsonPath("$.birthDate", equalTo("1990-05-17")))
                .withRequestBody(matchingJsonPath("$.role", equalTo("STUDENT"))));
    }

    @Test
    @DisplayName("the birth date can be left out: it is not sent to the API")
    void birthDateIsOptional() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(REGISTER_URL)).willReturn(aResponse().withStatus(202)));

        mockMvc.perform(registration().with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro/pendiente"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo(REGISTER_URL))
                .withRequestBody(matchingJsonPath("$.phone", equalTo("+5491100000000")))
                .withRequestBody(notMatching(".*birthDate.*")));
    }

    @Test
    @DisplayName("the form asks for first name, last name and phone as required fields and the birth date as optional")
    void personalDataFieldsAreRendered() throws Exception {
        String html = registerPage();

        assertThat(html)
                .containsPattern("<label[^>]*for=\"firstName\"[^>]*>Nombre</label>")
                .containsPattern("<label[^>]*for=\"lastName\"[^>]*>Apellido</label>")
                .containsPattern("<label[^>]*for=\"phone\"[^>]*>Teléfono</label>")
                .containsPattern("<label[^>]*for=\"birthDate\"[^>]*>Fecha de nacimiento \\(opcional\\)</label>")
                .containsPattern("<input[^>]*id=\"firstName\"[^>]*autocomplete=\"given-name\"[^>]*required")
                .containsPattern("<input[^>]*id=\"lastName\"[^>]*autocomplete=\"family-name\"[^>]*required")
                .containsPattern("<input[^>]*id=\"phone\"[^>]*type=\"tel\"[^>]*autocomplete=\"tel\"[^>]*required")
                .contains("Con código de país, por ejemplo +54 9 11 0000 0000");
        Matcher birthDate = BIRTH_DATE_INPUT.matcher(html);
        assertThat(birthDate.find()).isTrue();
        assertThat(birthDate.group()).contains("id=\"birthDate\"").doesNotContain("required");
        assertThat(html.indexOf("id=\"firstName\"")).isLessThan(html.indexOf("id=\"lastName\""));
        assertThat(html.indexOf("id=\"lastName\"")).isLessThan(html.indexOf("id=\"email\""));
        assertThat(html.indexOf("id=\"email\"")).isLessThan(html.indexOf("id=\"phone\""));
        assertThat(html.indexOf("id=\"phone\"")).isLessThan(html.indexOf("id=\"birthDate\""));
        assertThat(html.indexOf("id=\"birthDate\"")).isLessThan(html.indexOf("id=\"password\""));
    }

    @Test
    @DisplayName("missing or malformed personal data shows Spanish errors, keeps what was typed and skips the API")
    void invalidPersonalDataShowsSpanishErrorsAndKeepsValues() throws Exception {
        String body = mockMvc.perform(post("/registro")
                        .param("firstName", "Ana")
                        .param("lastName", " ")
                        .param("email", "ana@example.com")
                        .param("phone", "11 5555 0000")
                        .param("birthDate", LocalDate.now().plusDays(1).toString())
                        .param("password", "Secreta123")
                        .param("passwordConfirmation", "Otra12345")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("Ingresá tu apellido")
                .contains("Ingresá el teléfono con código de país")
                .contains("La fecha de nacimiento tiene que ser anterior a hoy")
                .containsPattern("<input[^>]*id=\"firstName\"[^>]*value=\"Ana\"")
                .containsPattern("<input[^>]*id=\"email\"[^>]*value=\"ana@example.com\"")
                .containsPattern("<input[^>]*id=\"phone\"[^>]*value=\"11 5555 0000\"")
                .containsPattern("<input[^>]*id=\"birthDate\"[^>]*value=\"" + LocalDate.now().plusDays(1) + "\"")
                .doesNotContain("Secreta123")
                .doesNotContain("Otra12345");
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(REGISTER_URL)))).isEmpty();
    }

    @Test
    @DisplayName("an empty form asks for each required field and nothing is requested of the optional birth date")
    void emptyFormAsksForRequiredFields() throws Exception {
        String body = mockMvc.perform(post("/registro").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("Ingresá tu nombre")
                .contains("Ingresá tu apellido")
                .contains("Ingresá tu teléfono")
                .doesNotContain("fecha de nacimiento válida");
    }

    private static MockHttpServletRequestBuilder registration() {
        return post("/registro")
                .param("firstName", "Ana")
                .param("lastName", "Prueba")
                .param("email", "ana@example.com")
                .param("phone", "+54 9 11 0000-0000")
                .param("password", "Secreta123")
                .param("passwordConfirmation", "Secreta123");
    }

    @Test
    @DisplayName("invalid input shows Spanish errors next to the fields and never reaches the API")
    void invalidInputShowsSpanishFieldErrors() throws Exception {
        String body = mockMvc.perform(post("/registro")
                        .param("email", "no-es-un-email")
                        .param("password", "corta")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("El formato del email es inválido")
                .contains("al menos 8 caracteres")
                .contains("una mayúscula")
                .contains("un número");
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(REGISTER_URL)))).isEmpty();
    }

    @Test
    @DisplayName("the form asks to repeat the password and flags a mismatch in Spanish")
    void mismatchedConfirmationShowsSpanishError() throws Exception {
        String body = mockMvc.perform(post("/registro")
                        .param("firstName", "Ana").param("lastName", "Prueba")
                        .param("phone", "+5491100000000")
                        .param("email", "ana@example.com")
                        .param("password", "Secreta123")
                        .param("passwordConfirmation", "Otra12345")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("name=\"passwordConfirmation\"").contains("Las contraseñas no coinciden");
        assertThat(WIRE_MOCK_SERVER.findAll(postRequestedFor(urlEqualTo(REGISTER_URL)))).isEmpty();
    }

    @Test
    @DisplayName("a rate-limited registration shows the Spanish 429 message without API internals")
    void rateLimitedRegistrationShowsSpanishMessage() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(REGISTER_URL))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60")
                        .withBody("{\"code\":\"ACTIVATION_RATE_LIMITED\"}")));

        String body = mockMvc.perform(registration().with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("demasiados intentos").doesNotContain("ACTIVATION_RATE_LIMITED");
    }

    @Test
    @DisplayName("the pending screen is public and never reveals whether the email already existed")
    void pendingScreenIsNeutral() throws Exception {
        String body = mockMvc.perform(get("/registro/pendiente"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Revisá tu correo").doesNotContain("ya está registrado").doesNotContain("@");
    }

    @Test
    @DisplayName("posting without a CSRF token is rejected")
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/registro").param("email", "ana@example.com").param("password", "Secreta123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only the registration routes are public: nothing beneath them opens up")
    void onlyRegistrationRoutesArePublic() throws Exception {
        mockMvc.perform(get("/registro/otra-cosa")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
        mockMvc.perform(post("/registro/pendiente").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
