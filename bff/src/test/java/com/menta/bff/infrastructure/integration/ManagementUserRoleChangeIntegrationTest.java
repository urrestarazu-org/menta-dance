package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The administrator's role change, {@code POST /gestion/usuarios/{id}/rol} and its form in the users list
 * (#378, part 5b-2b): who may use it, what the list renders for it, what it sends to the Auth API, how each
 * answer comes back to the list and what happens when the administrator changes their own role. Each case logs
 * in for real against the stubbed Auth API and reuses the session cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management role change Integration Tests")
class ManagementUserRoleChangeIntegrationTest extends BaseIntegrationTest {

    private static final String LIST = "/gestion/usuarios";
    private static final String USERS_API = "/api/v1/admin/users";
    private static final String ADA_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OWN_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final String ROLE_URL = LIST + "/" + ADA_ID + "/rol";
    private static final String ROLE_API = USERS_API + "/" + ADA_ID + "/role";
    private static final String SESSIONS_CLOSED = " Se cerraron sus sesiones: tendrá que iniciar sesión de nuevo.";
    private static final String ADA_CHANGED = "Cambiamos el rol de Ada Lovelace a Recepcionista." + SESSIONS_CLOSED;
    private static final String NO_STORE = "no-store";

    private static String account(String id, String email, String first, String last, String role) {
        return "{\"id\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"role\":\"%s\","
                .formatted(id, email, first, last, role)
                + "\"status\":\"ACTIVE\",\"demo\":false,\"createdAt\":\"2026-10-01T09:30:00\"}";
    }

    private static final String ADA_AS_RECEPTIONIST = account(ADA_ID, "ada@example.com", "Ada", "Lovelace",
            "RECEPTIONIST");

    private static void stubList(String... items) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(USERS_API)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"items\":[%s],\"page\":0,\"size\":20,\"totalElements\":%d,\"totalPages\":1}"
                        .formatted(String.join(",", items), items.length))));
    }

    private static void stubChange(int status, String contentType, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.patch(urlPathMatching(USERS_API + "/.*")).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", contentType).withBody(body)));
    }

    private static void stubProblem(int status, String code) {
        stubChange(status, "application/problem+json",
                "{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret-detail\","
                        .formatted(status) + "\"code\":\"%s\"}".formatted(code));
    }

    private Cookie logIn(String role) throws Exception {
        return logInWith(TestAccessTokens.withRole(role));
    }

    private Cookie logInAsAdminWithId(String id) throws Exception {
        return logInWith(TestAccessTokens.withPayload("{\"sub\":\"" + id + "\",\"role\":\"ADMIN\"}"));
    }

    private Cookie logInWith(String accessToken) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(accessToken))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private static MockHttpServletRequestBuilder change(Cookie session, String url, String role) {
        MockHttpServletRequestBuilder request = post(url).cookie(session).with(csrf());
        if (role != null) {
            request.param("role", role);
        }
        return request;
    }

    private String listOf(Cookie session) throws Exception {
        return mainOf(mockMvc.perform(get(LIST).cookie(session)).andReturn().getResponse().getContentAsString());
    }

    private static void verifyNoChangeRequested() {
        WIRE_MOCK_SERVER.verify(0, patchRequestedFor(urlPathMatching(USERS_API + "/.*")));
    }

    // ---- access ----

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on the role change, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);

        mockMvc.perform(change(logIn(role), ROLE_URL, "ADMIN")).andExpect(status().isForbidden());

        verifyNoChangeRequested();
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page, and the API is not asked")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(post(ROLE_URL).with(csrf()).param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));

        verifyNoChangeRequested();
    }

    @Test
    @DisplayName("a submission without a CSRF token is refused even for an administrator, and the API is not asked")
    void submissionNeedsCsrf() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);

        mockMvc.perform(post(ROLE_URL).cookie(logIn("ADMIN")).param("role", "ADMIN"))
                .andExpect(status().isForbidden());

        verifyNoChangeRequested();
    }

    // ---- the list's form ----

    @Test
    @DisplayName("every row has its own form: a labelled role select with the current role chosen, and a button")
    void rowsHaveAnAccessibleForm() throws Exception {
        stubList(account(ADA_ID, "ada@example.com", "Ada", "Lovelace", "RECEPTIONIST"),
                account(OWN_ID, "root@example.com", "Root", "Admin", "ADMIN"));

        MvcResult result = mockMvc.perform(get(LIST).cookie(logIn("ADMIN"))).andReturn();
        String main = mainOf(result.getResponse().getContentAsString());

        assertThat(main).containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*Cambiar rol\\s*</th>");
        assertRowForm(main, ADA_ID, "ada@example.com", "RECEPTIONIST");
        assertRowForm(main, OWN_ID, "root@example.com", "ADMIN");
        assertThat(main).containsPattern("<option[^>]*value=\"ADMIN\"[^>]*>\\s*Administrador\\s*</option>")
                .containsPattern("<option[^>]*value=\"INSTRUCTOR\"[^>]*>\\s*Profesor/a\\s*</option>")
                .containsPattern("<option[^>]*value=\"RECEPTIONIST\"[^>]*>\\s*Recepcionista\\s*</option>")
                .containsPattern("<option[^>]*value=\"STUDENT\"[^>]*>\\s*Alumno/a\\s*</option>")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Cambiar\\s*<span class=\"sr-only\">"
                        + " el rol de ada@example.com</span>\\s*</button>");
        assertThat(main.split("action=\"/gestion/usuarios/" + ADA_ID + "/rol\"", -1)).hasSize(2);
        assertThat(main.split("name=\"_csrf\"", -1).length - 1).as("one CSRF field per row form")
                .isEqualTo(2);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
    }

    @Test
    @DisplayName("the forms carry the filters and the page of the list, escaped, for the redirection")
    void formsCarryTheListState() throws Exception {
        stubList(account(ADA_ID, "ada@example.com", "Ada", "Lovelace", "RECEPTIONIST"));

        String main = mainOf(mockMvc.perform(get(LIST).cookie(logIn("ADMIN")).param("role", "RECEPTIONIST")
                        .param("q", "\"><script>x</script>"))
                .andReturn().getResponse().getContentAsString());

        assertThat(main)
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"filterRole\"[^>]*value=\"RECEPTIONIST\"")
                .containsPattern("<input[^>]*type=\"hidden\"[^>]*name=\"page\"[^>]*value=\"0\"")
                .contains("name=\"q\" value=\"&quot;&gt;&lt;script&gt;x&lt;/script&gt;\"")
                .doesNotContain("<script>x</script>");
    }

    @Test
    @DisplayName("without filters the hidden filter fields are empty, not the word null")
    void formsWithoutFilters() throws Exception {
        stubList(account(ADA_ID, "ada@example.com", "Ada", "Lovelace", "RECEPTIONIST"));

        String main = mainOf(mockMvc.perform(get(LIST).cookie(logIn("ADMIN"))).andReturn().getResponse()
                .getContentAsString());

        assertThat(main).containsPattern("<input[^>]*name=\"filterRole\"[^>]*value=\"\"")
                .doesNotContain("value=\"null\"");
    }

    private static void assertRowForm(String main, String id, String email, String currentRole) {
        assertThat(main)
                .containsPattern("<form[^>]*action=\"/gestion/usuarios/" + id + "/rol\"[^>]*method=\"post\"")
                .containsPattern("<label[^>]*class=\"sr-only\"[^>]*for=\"role-" + id + "\"[^>]*>\\s*Rol de "
                        + email + "\\s*</label>")
                .containsPattern("<select[^>]*id=\"role-" + id + "\"[^>]*name=\"role\"")
                .containsPattern("<option[^>]*value=\"" + currentRole + "\"[^>]*selected[^>]*>");
    }

    private static String alertWith(String message) {
        return "<[^>]*role=\"alert\"[^>]*>\\s*<p>" + message + "</p>";
    }

    // ---- success ----

    @Test
    @DisplayName("patches the account with the administrator's token and redirects to the list, nothing in the URL")
    void changesTheRole() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);

        MvcResult result = mockMvc.perform(change(logIn("ADMIN"), ROLE_URL, "RECEPTIONIST"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(LIST)).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, patchRequestedFor(urlEqualTo(ROLE_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withRequestBody(equalToJson("{\"role\":\"RECEPTIONIST\"}")));
    }

    @Test
    @DisplayName("the list confirms the change once, in a status region, and not on the next visit")
    void listShowsTheConfirmationOnce() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");
        mockMvc.perform(change(session, ROLE_URL, "RECEPTIONIST"));

        String first = listOf(session);
        String second = listOf(session);

        assertThat(first).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>" + ADA_CHANGED + "</p>")
                .doesNotContain("role=\"alert\"");
        assertThat(second).doesNotContain("Cambiamos el rol");
    }

    @Test
    @DisplayName("the confirmation escapes the name of the account")
    void confirmationEscapesTheName() throws Exception {
        stubChange(200, "application/json",
                account(ADA_ID, "ada@example.com", "<script>alert(1)</script>", "O'Brien", "RECEPTIONIST"));
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");
        mockMvc.perform(change(session, ROLE_URL, "RECEPTIONIST"));

        String main = listOf(session);

        assertThat(main).doesNotContain("<script>alert(1)</script>")
                .contains("Cambiamos el rol de &lt;script&gt;alert(1)&lt;/script&gt; O&#39;Brien a Recepcionista.");
    }

    @Test
    @DisplayName("an account without a name is called by its email in the confirmation")
    void confirmationFallsBackToTheEmail() throws Exception {
        stubChange(200, "application/json", "{\"id\":\"%s\",\"email\":\"ada@example.com\",\"firstName\":null,"
                .formatted(ADA_ID) + "\"lastName\":null,\"role\":\"STUDENT\",\"status\":\"ACTIVE\","
                + "\"createdAt\":\"2026-10-01T09:30:00\"}");
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");
        mockMvc.perform(change(session, ROLE_URL, "STUDENT"));

        assertThat(listOf(session)).contains("Cambiamos el rol de ada@example.com a Alumno/a.");
    }

    // ---- redirection ----

    @Test
    @DisplayName("the redirect keeps the filters and the page of the list, as a fixed path with three parameters")
    void redirectKeepsTheListState() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);

        mockMvc.perform(change(logIn("ADMIN"), ROLE_URL, "RECEPTIONIST")
                        .param("filterRole", "INSTRUCTOR").param("q", "ada lovelace").param("page", "2"))
                .andExpect(redirectedUrl(LIST + "?role=INSTRUCTOR&q=ada%20lovelace&page=2"));
    }

    @Test
    @DisplayName("the search text is encoded strictly: it cannot add parameters, a fragment or another target")
    void searchTextIsEncoded() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);

        mockMvc.perform(change(logIn("ADMIN"), ROLE_URL, "RECEPTIONIST")
                        .param("q", "a&role=ADMIN#x b+c=d/../"))
                .andExpect(redirectedUrl(LIST + "?q=a%26role%3DADMIN%23x%20b%2Bc%3Dd%2F..%2F"));
    }

    @ParameterizedTest(name = "[{index}] q={0}, page={1}, filterRole={2} is dropped")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "NULL|NULL|NULL",
        "'   '|-1|WIZARD",
        "''|abc|admin",
        "NULL|1e9|ROLE_ADMIN",
        "NULL|99999999999|'ADMIN,STUDENT'",
        "NULL|'1 '|''",
        "NULL|2.5|' '"})
    @DisplayName("blank text, a page that is not a non-negative number and a role that is not one are dropped")
    void invalidListStateIsDropped(String text, String page, String filterRole) throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);
        MockHttpServletRequestBuilder request = change(logIn("ADMIN"), ROLE_URL, "RECEPTIONIST");
        if (text != null) {
            request.param("q", text);
        }
        if (page != null) {
            request.param("page", page);
        }
        if (filterRole != null) {
            request.param("filterRole", filterRole);
        }

        MvcResult result = mockMvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(LIST);
    }

    @Test
    @DisplayName("a text longer than the search box allows is dropped, never carried")
    void overlongTextIsDropped() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);

        mockMvc.perform(change(logIn("ADMIN"), ROLE_URL, "RECEPTIONIST").param("q", "x".repeat(101)))
                .andExpect(redirectedUrl(LIST));
    }

    @Test
    @DisplayName("whatever the client sends, the redirect is the fixed path, on this host, with only role, q and page")
    void redirectIsNeverTheClients() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        MvcResult result = mockMvc.perform(change(session, ROLE_URL, "RECEPTIONIST")
                        .param("redirect", "https://evil.example/").param("returnTo", "//evil.example")
                        .param("next", "/logout").param("url", "https://evil.example/")
                        .param("q", "https://evil.example/\r\nLocation: https://evil.example/")
                        .param("filterRole", "INSTRUCTOR").param("page", "1"))
                .andReturn();

        URI location = URI.create(result.getResponse().getRedirectedUrl());
        assertThat(location.getScheme()).isNull();
        assertThat(location.getHost()).isNull();
        assertThat(location.getPath()).isEqualTo(LIST);
        assertThat(location.getRawFragment()).isNull();
        assertThat(UriComponentsBuilder.fromUri(location).build().getQueryParams().keySet())
                .containsExactlyInAnyOrder("role", "q", "page");
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("\r").doesNotContain("\n")
                .doesNotContain("redirect").doesNotContain("returnTo");
        assertThat(result.getResponse().getHeaders("Location")).hasSize(1);
    }

    // ---- local validation ----

    @ParameterizedTest(name = "[{index}] id {0}")
    @ValueSource(strings = {"not-a-uuid", "1", "1-1-1-1-1", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5g"})
    @DisplayName("an id that is not a UUID comes back as a rejection on the list, and the API is not asked")
    void malformedIdIsRefused(String id) throws Exception {
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(change(session, LIST + "/" + id + "/rol", "ADMIN")).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session)).containsPattern(alertWith("Revisá los datos e intentá de nuevo."));
        verifyNoChangeRequested();
    }

    @ParameterizedTest(name = "[{index}] role {0}")
    @ValueSource(strings = {"WIZARD", "admin", "", " "})
    @DisplayName("a missing or unknown role comes back as a rejection on the list, and the API is not asked")
    void unknownRoleIsRefused(String role) throws Exception {
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(change(session, ROLE_URL, role)).andExpect(redirectedUrl(LIST));
        mockMvc.perform(change(session, ROLE_URL, null)).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session)).contains("Revisá los datos e intentá de nuevo.");
        verifyNoChangeRequested();
    }

    // ---- API failures ----

    @Test
    @DisplayName("the last administrator cannot be demoted: an alert on the list with its own words, no API detail")
    void lastAdminIsAnAlert(CapturedOutput output) throws Exception {
        stubProblem(409, "LAST_ADMIN_CANNOT_BE_DEMOTED");
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(change(session, ROLE_URL, "STUDENT")).andExpect(redirectedUrl(LIST));
        String first = listOf(session);
        String second = listOf(session);

        assertThat(first).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No se puede quitar el rol de administrador"
                        + " al único administrador activo.</p>")
                .doesNotContain("role=\"status\"").doesNotContain("Cambiamos el rol")
                .doesNotContain("secret-detail").doesNotContain("LAST_ADMIN_CANNOT_BE_DEMOTED");
        assertThat(second).doesNotContain("único administrador activo");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain("ada@example.com");
    }

    @Test
    @DisplayName("an account that does not exist is an alert on the list")
    void userNotFoundIsAnAlert() throws Exception {
        stubProblem(404, "USER_NOT_FOUND");
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(change(session, ROLE_URL, "STUDENT")).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session)).containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No encontramos esa cuenta.</p>")
                .doesNotContain("secret-detail").doesNotContain("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("a rejection by the API is an alert on the list, with no API detail")
    void apiRejectionIsAnAlert() throws Exception {
        stubProblem(400, "INVALID_REQUEST");
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(change(session, ROLE_URL, "STUDENT")).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session)).containsPattern(alertWith("Revisá los datos e intentá de nuevo."))
                .doesNotContain("secret-detail").doesNotContain("INVALID_REQUEST");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {401, 403, 429, 500, 503})
    @DisplayName("any other failure of the API is an alert on the list, with no API detail, token or data in the logs")
    void apiUnavailableIsAnAlert(int apiStatus, CapturedOutput output) throws Exception {
        stubProblem(apiStatus, "SOMETHING_ELSE");
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(change(session, ROLE_URL, "STUDENT")).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session))
                .containsPattern("<[^>]*role=\"alert\"[^>]*>\\s*<p>No pudimos cambiar el rol. "
                        + "Probá de nuevo en unos minutos.</p>")
                .doesNotContain("secret-detail").doesNotContain("SOMETHING_ELSE");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain("ada@example.com")
                .doesNotContain(TestAccessTokens.withRole("ADMIN"));
    }

    @Test
    @DisplayName("a 404 or 409 with another code is unavailable, not a typed answer")
    void otherCodesAreUnavailable() throws Exception {
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logIn("ADMIN");

        stubProblem(404, "SOMETHING_ELSE");
        mockMvc.perform(change(session, ROLE_URL, "STUDENT"));
        stubProblem(409, "SOMETHING_ELSE");
        mockMvc.perform(change(session, ROLE_URL, "STUDENT"));

        assertThat(listOf(session)).contains("No pudimos cambiar el rol.").doesNotContain("No encontramos")
                .doesNotContain("único administrador");
    }

    // ---- the administrator changes their own role ----

    @Test
    @DisplayName("changing one's own role closes the session and sends the administrator to the login, with a notice")
    void ownRoleChangeEndsTheSession() throws Exception {
        stubChange(200, "application/json", account(OWN_ID, "root@example.com", "Root", "Admin", "INSTRUCTOR"));
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logInAsAdminWithId(OWN_ID);

        MvcResult result = mockMvc.perform(change(session, LIST + "/" + OWN_ID + "/rol", "INSTRUCTOR"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?roleChanged")).andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains(NO_STORE);
        WIRE_MOCK_SERVER.verify(1, patchRequestedFor(urlEqualTo(USERS_API + "/" + OWN_ID + "/role"))
                .withRequestBody(equalToJson("{\"role\":\"INSTRUCTOR\"}")));
        // The old cookie is worth nothing: the list sends it to the login and never asks the API.
        mockMvc.perform(get(LIST).cookie(session)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("the login page explains why the administrator is there, in a status region")
    void loginExplainsTheOwnRoleChange() throws Exception {
        String main = mainOf(mockMvc.perform(get("/login").param("roleChanged", ""))
                .andReturn().getResponse().getContentAsString());

        assertThat(main).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Cambiaste tu propio rol. "
                + "Iniciá sesión de nuevo.</p>");
        assertThat(mainOf(mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString()))
                .doesNotContain("Cambiaste tu propio rol");
    }

    @Test
    @DisplayName("choosing one's own current role changes nothing, so the session stays and the list confirms")
    void ownRoleUnchangedKeepsTheSession() throws Exception {
        stubChange(200, "application/json", account(OWN_ID, "root@example.com", "Root", "Admin", "ADMIN"));
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logInAsAdminWithId(OWN_ID);

        mockMvc.perform(change(session, LIST + "/" + OWN_ID + "/rol", "ADMIN")).andExpect(redirectedUrl(LIST));

        mockMvc.perform(get(LIST).cookie(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the last administrator demoting themself is refused, and their session stays")
    void lastAdminSelfDemotionKeepsTheSession() throws Exception {
        stubProblem(409, "LAST_ADMIN_CANNOT_BE_DEMOTED");
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logInAsAdminWithId(OWN_ID);

        mockMvc.perform(change(session, LIST + "/" + OWN_ID + "/rol", "STUDENT")).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session)).contains("No se puede quitar el rol de administrador");
    }

    @Test
    @DisplayName("changing another account's role does not touch the administrator's own session")
    void otherAccountKeepsTheSession() throws Exception {
        stubChange(200, "application/json", ADA_AS_RECEPTIONIST);
        stubList(ADA_AS_RECEPTIONIST);
        Cookie session = logInAsAdminWithId(OWN_ID);

        mockMvc.perform(change(session, ROLE_URL, "RECEPTIONIST")).andExpect(redirectedUrl(LIST));

        assertThat(listOf(session)).contains("Cambiamos el rol de Ada Lovelace");
    }
}
