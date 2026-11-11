package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The administrator's account creation form, {@code GET /gestion/usuarios/nuevo} and
 * {@code POST /gestion/usuarios} (#378, part 5b-2a): who may use it, what it renders, what it sends to the
 * Auth API and what each answer looks like, without ever showing the API's error body in the browser or
 * writing personal data to a log. Each case logs in for real against the stubbed Auth API and reuses the
 * session cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management user creation Integration Tests")
class ManagementUserCreateIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/usuarios";
    private static final String FORM = "/gestion/usuarios/nuevo";
    private static final String USERS_API = "/api/v1/admin/users";
    private static final String CREATED_MESSAGE =
            "Creamos la cuenta de Ada Lovelace. Le enviamos un correo para que la active y elija su contraseña.";
    private static final String CREATED_BODY = "{\"id\":\"5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f\","
            + "\"email\":\"recepcion@example.com\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\","
            + "\"role\":\"RECEPTIONIST\",\"status\":\"PENDING_ACTIVATION\",\"demo\":false,"
            + "\"createdAt\":\"2026-11-10T09:30:00\"}";
    private static final String EMPTY_PAGE =
            "{\"items\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}";

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private static void stubCreate(int status, String contentType, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlPathEqualTo(USERS_API)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", contentType).withBody(body)));
    }

    private static void stubProblem(int status, String code) {
        stubCreate(status, "application/problem+json",
                "{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        .formatted(status) + "\"code\":\"%s\"}".formatted(code));
    }

    private static void stubEmptyList() {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(USERS_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody(EMPTY_PAGE)));
    }

    private static MockHttpServletRequestBuilder submission(
            Cookie session, String email, String firstName, String lastName, String role) {
        MockHttpServletRequestBuilder request = post(LIST).cookie(session).with(csrf());
        if (email != null) {
            request.param("email", email);
        }
        if (firstName != null) {
            request.param("firstName", firstName);
        }
        if (lastName != null) {
            request.param("lastName", lastName);
        }
        if (role != null) {
            request.param("role", role);
        }
        return request;
    }

    private MvcResult validSubmission(Cookie session) throws Exception {
        return mockMvc.perform(submission(session, "recepcion@example.com", "Ada", "Lovelace", "RECEPTIONIST"))
                .andReturn();
    }

    private String formOf(Cookie session) throws Exception {
        return mainOf(mockMvc.perform(get(FORM).cookie(session)).andReturn().getResponse().getContentAsString());
    }

    // ---- access ----

    @Test
    @DisplayName("an administrator opens the form")
    void adminOpensTheForm() throws Exception {
        mockMvc.perform(get(FORM).cookie(logIn("ADMIN"))).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on the form and on the submission, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        stubCreate(201, "application/json", CREATED_BODY);
        Cookie session = logIn(role);

        mockMvc.perform(get(FORM).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(submission(session, "recepcion@example.com", "Ada", "Lovelace", "ADMIN"))
                .andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page, on the form and on the submission")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(FORM)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post(LIST).with(csrf()).param("email", "recepcion@example.com"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("a submission without a CSRF token is refused even for an administrator, and the API is not asked")
    void submissionNeedsCsrf() throws Exception {
        stubCreate(201, "application/json", CREATED_BODY);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(post(LIST).cookie(session).param("email", "recepcion@example.com")
                        .param("firstName", "Ada").param("lastName", "Lovelace").param("role", "ADMIN"))
                .andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(USERS_API)));
    }

    // ---- the form ----

    @Test
    @DisplayName("renders an accessible form inside the management layout, with Usuarios as the current section")
    void rendersTheForm() throws Exception {
        MvcResult result = mockMvc.perform(get(FORM).cookie(logIn("ADMIN"))).andReturn();
        String html = result.getResponse().getContentAsString();
        String main = mainOf(html);

        assertThat(html).contains("<title>Nueva cuenta - Menta Dance</title>");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Nueva cuenta\\s*</h1>")
                .contains("<nav aria-label=\"Gestión\"")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Usuarios\\s*</a>")
                .doesNotContainPattern("aria-current=\"page\"[^>]*>\\s*Inicio");
        assertThat(main).containsPattern("<form[^>]*action=\"/gestion/usuarios\"")
                .containsPattern("<form[^>]*method=\"post\"")
                .containsPattern("<form[^>]*novalidate")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"_csrf\"");
        for (String field : new String[] {"firstName", "lastName", "email", "role"}) {
            assertThat(main).containsPattern("<label[^>]*for=\"" + field + "\"");
        }
        assertThat(main).containsPattern("<input[^>]*id=\"email\"[^>]*type=\"email\"")
                .containsPattern("<input[^>]*id=\"firstName\"[^>]*name=\"firstName\"")
                .containsPattern("<input[^>]*id=\"lastName\"[^>]*name=\"lastName\"")
                .containsPattern("<select[^>]*id=\"role\"[^>]*name=\"role\"")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Crear cuenta\\s*</button>")
                .containsPattern("<a[^>]*href=\"/gestion/usuarios\"[^>]*>\\s*Cancelar\\s*</a>")
                .doesNotContain("password");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    @DisplayName("the role select offers the three staff roles in Spanish, and neither Alumno nor a role preselected")
    void roleSelectOffersStaffRoles() throws Exception {
        String main = formOf(logIn("ADMIN"));

        assertThat(main).containsPattern("<option[^>]*value=\"\"[^>]*>\\s*Elegí un rol\\s*</option>")
                .containsPattern("<option[^>]*value=\"INSTRUCTOR\"[^>]*>\\s*Profesor/a\\s*</option>")
                .containsPattern("<option[^>]*value=\"RECEPTIONIST\"[^>]*>\\s*Recepcionista\\s*</option>")
                .containsPattern("<option[^>]*value=\"ADMIN\"[^>]*>\\s*Administrador\\s*</option>")
                .doesNotContain("STUDENT").doesNotContain("Alumno")
                .doesNotContain("selected");
    }

    @Test
    @DisplayName("a pristine form has no error markup and keeps autofocus on its first field")
    void pristineForm() throws Exception {
        String main = formOf(logIn("ADMIN"));

        assertThat(main).doesNotContain("data-error-summary").doesNotContain("aria-invalid")
                .doesNotContain("role=\"alert\"");
        assertThat(main.split("autofocus=", -1)).as("one autofocus attribute").hasSize(2);
    }

    // ---- success ----

    @Test
    @DisplayName("sends the four fields with the administrator's token and redirects to the list, nothing in the URL")
    void createsTheAccount() throws Exception {
        stubCreate(201, "application/json", CREATED_BODY);

        MvcResult result = mockMvc.perform(submission(logIn("ADMIN"),
                        "  recepcion@example.com ", " Ada ", "Lovelace ", "RECEPTIONIST"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST)).andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("recepcion").doesNotContain("Ada");
        WIRE_MOCK_SERVER.verify(1, postRequestedFor(urlPathEqualTo(USERS_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{\"email\":\"recepcion@example.com\",\"firstName\":\"Ada\","
                        + "\"lastName\":\"Lovelace\",\"role\":\"RECEPTIONIST\"}")));
    }

    @Test
    @DisplayName("the list confirms the creation once, in a status region, and not on the next visit")
    void listShowsTheConfirmationOnce() throws Exception {
        stubCreate(201, "application/json", CREATED_BODY);
        stubEmptyList();
        Cookie session = logIn("ADMIN");
        validSubmission(session);

        String first = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());
        String second = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());

        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>" + CREATED_MESSAGE + "</p>");
        assertThat(second).doesNotContain("Creamos la cuenta");
    }

    @Test
    @DisplayName("the confirmation escapes the name typed by the administrator")
    void confirmationEscapesTheName() throws Exception {
        stubCreate(201, "application/json", CREATED_BODY);
        stubEmptyList();
        Cookie session = logIn("ADMIN");
        mockMvc.perform(submission(session, "recepcion@example.com", "<script>alert(1)</script>", "O'Brien", "ADMIN"));

        String main = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());

        assertThat(main).doesNotContain("<script>alert(1)</script>")
                .contains("Creamos la cuenta de &lt;script&gt;alert(1)&lt;/script&gt; O&#39;Brien.");
    }

    // ---- local validation ----

    @Test
    @DisplayName("an empty submission returns the form with a focused summary and one error per field, and no API call")
    void emptySubmissionShowsAllErrors() throws Exception {
        stubCreate(201, "application/json", CREATED_BODY);

        MvcResult result = mockMvc.perform(submission(logIn("ADMIN"), null, null, null, null))
                .andExpect(status().isOk()).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(main).contains("data-error-summary").contains("Hay 4 errores en el formulario")
                .contains("tabindex=\"-1\"")
                .containsPattern("<a href=\"#firstName\">Ingresá el nombre</a>")
                .containsPattern("<a href=\"#lastName\">Ingresá el apellido</a>")
                .containsPattern("<a href=\"#email\">Ingresá el email</a>")
                .containsPattern("<a href=\"#role\">Elegí un rol de la lista</a>");
        for (String field : new String[] {"firstName", "lastName", "email", "role"}) {
            assertThat(main).containsPattern("<(input|select)[^>]*id=\"" + field + "\"[^>]*aria-invalid=\"true\"")
                    .containsPattern("<(input|select)[^>]*id=\"" + field + "\"[^>]*aria-describedby=\""
                            + field + "-help\"")
                    .contains("id=\"" + field + "-help\"");
        }
        assertThat(main).doesNotContain("autofocus");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("a local error keeps what was typed, escaped, and the chosen role")
    void localErrorKeepsTheData() throws Exception {
        String main = mainOf(mockMvc.perform(submission(logIn("ADMIN"),
                        "\"><script>alert(1)</script>", "Ada", "Love\"lace", "INSTRUCTOR"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Hay 1 error en el formulario").contains("El formato del email es inválido")
                .doesNotContain("<script>alert(1)</script>")
                .contains("value=\"&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;\"")
                .containsPattern("<input[^>]*id=\"firstName\"[^>]*value=\"Ada\"")
                .contains("value=\"Love&quot;lace\"")
                .containsPattern("<option[^>]*value=\"INSTRUCTOR\"[^>]*selected[^>]*>")
                .doesNotContainPattern("<option[^>]*value=\"ADMIN\"[^>]*selected");
    }

    @Test
    @DisplayName("STUDENT, even sent by hand, is refused locally and the API is not asked")
    void studentIsRefusedLocally() throws Exception {
        String main = mainOf(mockMvc.perform(submission(logIn("ADMIN"),
                        "alumna@example.com", "Ada", "Lovelace", "STUDENT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Elegí un rol de la lista");
        WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(USERS_API)));
    }

    // ---- API answers ----

    @Test
    @DisplayName("an email that already has an account is an error on the email field, with the data kept")
    void emailAlreadyRegistered(CapturedOutput output) throws Exception {
        stubProblem(409, "EMAIL_ALREADY_REGISTERED");

        MvcResult result = mockMvc.perform(submission(logIn("ADMIN"),
                "recepcion@example.com", "Ada", "Lovelace", "RECEPTIONIST")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(main).contains("Hay 1 error en el formulario")
                .containsPattern("<a href=\"#email\">Ya existe una cuenta con ese email.</a>")
                .containsPattern("<p[^>]*class=\"field-error\"[^>]*>Ya existe una cuenta con ese email.</p>")
                .containsPattern("<input[^>]*id=\"email\"[^>]*aria-invalid=\"true\"")
                .containsPattern("<input[^>]*id=\"email\"[^>]*value=\"recepcion@example.com\"")
                .containsPattern("<input[^>]*id=\"firstName\"[^>]*value=\"Ada\"")
                .containsPattern("<option[^>]*value=\"RECEPTIONIST\"[^>]*selected[^>]*>")
                .doesNotContain("secret-detail").doesNotContain("EMAIL_ALREADY_REGISTERED");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(output.getAll()).doesNotContain("recepcion@example.com").doesNotContain("Lovelace");
    }

    @Test
    @DisplayName("a rejection by the API is a general error of the form, with the data kept and no API detail")
    void apiRejection() throws Exception {
        stubProblem(400, "INVALID_REQUEST");

        MvcResult result = mockMvc.perform(submission(logIn("ADMIN"),
                "recepcion@example.com", "Ada", "Lovelace", "ADMIN")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(main).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>Revisá los datos e intentá de nuevo.</p>")
                .containsPattern("<input[^>]*id=\"email\"[^>]*value=\"recepcion@example.com\"")
                .doesNotContain("secret-detail").doesNotContain("INVALID_REQUEST")
                .doesNotContain("data-error-summary");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    @DisplayName("any other failure of the API is a 503 with a general notice, the data kept and no API detail")
    void apiUnavailable(int apiStatus, CapturedOutput output) throws Exception {
        stubProblem(apiStatus, "SOMETHING_ELSE");

        MvcResult result = mockMvc.perform(submission(logIn("ADMIN"),
                "recepcion@example.com", "Ada", "Lovelace", "ADMIN")).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(main)
                .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No pudimos crear la cuenta. "
                        + "Probá de nuevo en unos minutos.</p>")
                .containsPattern("<input[^>]*id=\"email\"[^>]*value=\"recepcion@example.com\"")
                .containsPattern("<option[^>]*value=\"ADMIN\"[^>]*selected[^>]*>")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain("recepcion@example.com")
                .doesNotContain("Lovelace").doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("a 409 with another code is unavailable, not an email error")
    void otherConflictIsUnavailable() throws Exception {
        stubProblem(409, "SOMETHING_ELSE");

        mockMvc.perform(submission(logIn("ADMIN"), "recepcion@example.com", "Ada", "Lovelace", "ADMIN"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("a failed creation leaves no confirmation behind for the list")
    void failedCreationLeavesNoConfirmation() throws Exception {
        stubProblem(500, "SOMETHING_ELSE");
        stubEmptyList();
        Cookie session = logIn("ADMIN");
        validSubmission(session);

        String list = mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse()
                .getContentAsString());

        assertThat(list).doesNotContain("Creamos la cuenta");
    }
}
