package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * The administrator's users list, {@code GET /gestion/usuarios} (#378, part 5b-1): who may open it, what it
 * renders for each answer of the Auth API, and what it leaves out of the browser, the logs and the cache.
 * Each case logs in for real against the stubbed Auth API and reuses the session cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Management users list Integration Tests")
class ManagementUsersIntegrationTest extends BaseIntegrationTest {

    private static final String PATH = "/gestion/usuarios";
    private static final String USERS_API = "/api/v1/admin/users";

    private static final String ONE_PAGE = page(0, 1, 2,
            user("ada@example.com", "Ada", "Lovelace", "RECEPTIONIST", "ACTIVE", "2026-10-01T09:30:00"),
            user("root@example.com", null, null, "ADMIN", "PENDING_ACTIVATION", "2026-10-02T10:15:30"));

    private static String user(
            String email, String first, String last, String role, String status, String createdAt) {
        return "{\"id\":\"id-%s\",\"email\":\"%s\",\"firstName\":%s,\"lastName\":%s,\"role\":\"%s\","
                .formatted(email, email, jsonOrNull(first), jsonOrNull(last), role)
                + "\"status\":\"%s\",\"demo\":false,\"createdAt\":\"%s\"}".formatted(status, createdAt);
    }

    private static String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String page(int index, int totalPages, long total, String... items) {
        return "{\"items\":[%s],\"page\":%d,\"size\":20,\"totalElements\":%d,\"totalPages\":%d}"
                .formatted(String.join(",", items), index, total, totalPages);
    }

    private static void stubUsers(int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlPathEqualTo(USERS_API)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

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

    /** Opens the list as an administrator; {@code params} are name, value pairs sent as they are typed. */
    private MvcResult adminRequest(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(PATH).cookie(logIn("ADMIN"));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String mainOfAdminRequest(String... params) throws Exception {
        return mainOf(adminRequest(params).getResponse().getContentAsString());
    }

    // ---- access ----

    @Test
    @DisplayName("an administrator opens the list")
    void adminOpensTheList() throws Exception {
        stubUsers(200, ONE_PAGE);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(get(PATH).cookie(session)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} is refused {1}")
    @ValueSource(strings = {"INSTRUCTOR", "RECEPTIONIST", "STUDENT"})
    @DisplayName("every other role gets a 403 on the list, below it and on any verb, and the API is never asked")
    void otherRolesAreRefused(String role) throws Exception {
        stubUsers(200, ONE_PAGE);
        Cookie session = logIn(role);

        mockMvc.perform(get(PATH).cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/otra").cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH).cookie(session).with(csrf())).andExpect(status().isForbidden());

        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(USERS_API)));
    }

    // ---- management layout ----

    @Test
    @DisplayName("the Usuarios section is the current page, Inicio is not, and the title names the screen")
    void usersSectionIsCurrent() throws Exception {
        stubUsers(200, ONE_PAGE);
        Cookie session = logIn("ADMIN");

        String html = mockMvc.perform(get(PATH).cookie(session)).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<title>Usuarios - Menta Dance</title>");
        String main = mainOf(html);
        assertThat(main).contains("<nav aria-label=\"Gestión\"")
                .containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Usuarios\\s*</a>")
                .containsPattern("<a[^>]*href=\"/gestion/usuarios\"[^>]*>\\s*Usuarios\\s*</a>")
                .containsPattern("<a[^>]*href=\"/gestion\"[^>]*>\\s*Inicio\\s*</a>")
                .doesNotContainPattern("aria-current=\"page\"[^>]*>\\s*Inicio");
        assertThat(main).containsPattern("<h1[^>]*>\\s*Usuarios\\s*</h1>");
    }

    @Test
    @DisplayName("the home links the Usuarios section to an administrator and to nobody else")
    void homeLinksTheSectionOnlyForAdmins() throws Exception {
        String adminHome = mainOf(mockMvc.perform(get("/gestion").cookie(logIn("ADMIN")))
                .andReturn().getResponse().getContentAsString());
        String instructorHome = mainOf(mockMvc.perform(get("/gestion").cookie(logIn("INSTRUCTOR")))
                .andReturn().getResponse().getContentAsString());

        assertThat(adminHome).containsPattern("<a[^>]*href=\"/gestion/usuarios\"[^>]*>\\s*Usuarios\\s*</a>")
                .doesNotContainPattern("aria-current=\"page\"[^>]*>\\s*Usuarios");
        assertThat(instructorHome).doesNotContain("/gestion/usuarios");
    }

    // ---- table ----

    @Test
    @DisplayName("renders an accessible table of accounts, with role, status and date in Spanish")
    void rendersTheTable() throws Exception {
        stubUsers(200, ONE_PAGE);

        String main = mainOfAdminRequest();

        assertThat(main).contains("<table").contains("scope=\"col\"")
                .containsPattern("<caption[^>]*>\\s*Usuarios \\(<span[^>]*>2</span> en total\\)\\s*</caption>");
        for (String column : new String[] {"Email", "Nombre y apellido", "Rol", "Estado", "Alta"}) {
            assertThat(main).containsPattern("<th[^>]*scope=\"col\"[^>]*>\\s*" + column + "\\s*</th>");
        }
        assertThat(main).contains("ada@example.com").contains("Ada Lovelace").contains("Recepcionista")
                .contains("Activo").contains("1 de octubre de 2026")
                .contains("root@example.com").contains("Sin nombre").contains("Administrador")
                .contains("Pendiente de activación").contains("2 de octubre de 2026")
                .containsPattern("<time[^>]*datetime=\"2026-10-01\"");
        assertThat(main).doesNotContain(">RECEPTIONIST<").doesNotContain("PENDING_ACTIVATION")
                .doesNotContain("No hay usuarios");
    }

    @Test
    @DisplayName("asks the API with the administrator's own token, the first page and the fixed size")
    void asksTheApiWithTheSessionToken() throws Exception {
        stubUsers(200, ONE_PAGE);

        adminRequest();

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(USERS_API))
                .withHeader("Authorization", equalTo("Bearer " + TestAccessTokens.withRole("ADMIN")))
                .withQueryParam("page", equalTo("0")).withQueryParam("size", equalTo("20")));
    }

    @Test
    @DisplayName("names and emails are escaped: markup typed by a user never reaches the page as markup")
    void escapesUserData() throws Exception {
        stubUsers(200, page(0, 1, 1, user("x@example.com", "<script>alert(1)</script>", "O'Brien & \\\"Co\\\"",
                "INSTRUCTOR", "ACTIVE", "2026-10-01T09:30:00")));

        String main = mainOfAdminRequest();

        assertThat(main).doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("&amp; &quot;Co&quot;");
    }

    @Test
    @DisplayName("a search text is echoed escaped in its field")
    void escapesTheEchoedText() throws Exception {
        stubUsers(200, ONE_PAGE);

        String main = mainOfAdminRequest("q", "\"><script>alert(1)</script>");

        assertThat(main).doesNotContain("<script>alert(1)</script>")
                .contains("value=\"&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;\"");
    }

    // ---- account creation entry point ----

    @Test
    @DisplayName("links the creation form with a Nueva cuenta button, also while the API is down")
    void linksTheCreationForm() throws Exception {
        stubUsers(200, ONE_PAGE);
        String main = mainOfAdminRequest();
        stubUsers(503, "{}");
        String degraded = mainOfAdminRequest();

        assertThat(main).containsPattern("<a[^>]*href=\"/gestion/usuarios/nuevo\"[^>]*>\\s*Nueva cuenta\\s*</a>");
        assertThat(degraded).containsPattern("<a[^>]*href=\"/gestion/usuarios/nuevo\"[^>]*>\\s*Nueva cuenta\\s*</a>");
    }

    @Test
    @DisplayName("shows the confirmation of a creation in a status region, escaped, and nothing without one")
    void showsTheCreationConfirmation() throws Exception {
        stubUsers(200, ONE_PAGE);
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlPathEqualTo(USERS_API)).willReturn(aResponse().withStatus(201)));
        Cookie session = logIn("ADMIN");
        String plain = mainOf(mockMvc.perform(get(PATH).cookie(session))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post(PATH).cookie(session).with(csrf()).param("email", "ada@example.com")
                .param("firstName", "<b>Ada</b>").param("lastName", "& Co.").param("role", "ADMIN"));
        String confirmed = mainOf(mockMvc.perform(get(PATH).cookie(session))
                .andReturn().getResponse().getContentAsString());

        assertThat(plain).doesNotContain("role=\"status\"").doesNotContain("Creamos la cuenta");
        assertThat(confirmed).containsPattern("<[^>]*role=\"status\"[^>]*>\\s*<p>Creamos la cuenta de "
                        + "&lt;b&gt;Ada&lt;/b&gt; &amp; Co\\.\\. Le enviamos un correo para que la active y elija "
                        + "su contraseña\\.</p>")
                .doesNotContain("<b>Ada</b>");
    }

    // ---- filters ----

    @Test
    @DisplayName("the filter form is a GET with a labelled search box, a role select with Todos, Buscar and Limpiar")
    void rendersTheFilterForm() throws Exception {
        stubUsers(200, ONE_PAGE);

        String main = mainOfAdminRequest();

        assertThat(main).containsPattern("<form[^>]*action=\"/gestion/usuarios\"")
                .containsPattern("<form[^>]*method=\"get\"")
                .doesNotContain("_csrf")
                .containsPattern("<label[^>]*for=\"q\"[^>]*>[^<]*Buscar por email o nombre[^<]*</label>")
                .containsPattern("<input[^>]*id=\"q\"[^>]*name=\"q\"[^>]*maxlength=\"100\"")
                .containsPattern("<label[^>]*for=\"role\"[^>]*>\\s*Rol\\s*</label>")
                .containsPattern("<select[^>]*id=\"role\"[^>]*name=\"role\"")
                .containsPattern("<option value=\"\"[^>]*>\\s*Todos\\s*</option>")
                .containsPattern("<option value=\"ADMIN\"[^>]*>\\s*Administrador\\s*</option>")
                .containsPattern("<option value=\"INSTRUCTOR\"[^>]*>\\s*Profesor/a\\s*</option>")
                .containsPattern("<option value=\"RECEPTIONIST\"[^>]*>\\s*Recepcionista\\s*</option>")
                .containsPattern("<option value=\"STUDENT\"[^>]*>\\s*Alumno/a\\s*</option>")
                .containsPattern("<button[^>]*type=\"submit\"[^>]*>\\s*Buscar\\s*</button>")
                .containsPattern("<a[^>]*href=\"/gestion/usuarios\"[^>]*>\\s*Limpiar\\s*</a>");
        assertThat(main).doesNotContainPattern("<option[^>]*selected[^>]*>\\s*Administrador");
    }

    @Test
    @DisplayName("the filters in the query string reach the API and stay in the form")
    void filtersReachTheApiAndStay() throws Exception {
        stubUsers(200, ONE_PAGE);

        String main = mainOfAdminRequest("role", "INSTRUCTOR", "q", "ada", "page", "0");

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(USERS_API))
                .withQueryParam("role", equalTo("INSTRUCTOR")).withQueryParam("q", equalTo("ada")));
        assertThat(main).containsPattern("<option[^>]*value=\"INSTRUCTOR\"[^>]*selected[^>]*>")
                .containsPattern("<input[^>]*name=\"q\"[^>]*value=\"ada\"");
    }

    @Test
    @DisplayName("an unknown role and a blank text are no filter at all")
    void unknownRoleAndBlankTextAreIgnored() throws Exception {
        stubUsers(200, ONE_PAGE);

        mainOfAdminRequest("role", "WIZARD", "q", "  ");

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(USERS_API))
                .withQueryParam("role", WireMock.absent()).withQueryParam("q", WireMock.absent()));
    }

    @Test
    @DisplayName("the search text is cut to 100 characters before the API is called")
    void textIsCut() throws Exception {
        stubUsers(200, ONE_PAGE);

        mainOfAdminRequest("q", "y".repeat(300));

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(USERS_API))
                .withQueryParam("q", equalTo("y".repeat(100))));
    }

    @Test
    @DisplayName("a page that is not a number is a 400, not a server error")
    void pageMustBeANumber() throws Exception {
        Cookie session = logIn("ADMIN");

        mockMvc.perform(get(PATH).param("page", "abc").cookie(session)).andExpect(status().isBadRequest());

        WIRE_MOCK_SERVER.verify(0, getRequestedFor(urlPathEqualTo(USERS_API)));
    }

    @Test
    @DisplayName("a negative page reads as the first")
    void negativePageIsTheFirst() throws Exception {
        stubUsers(200, ONE_PAGE);

        mainOfAdminRequest("page", "-3");

        WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo(USERS_API)).withQueryParam("page", equalTo("0")));
    }

    // ---- empty and failure states ----

    @Test
    @DisplayName("no match: the empty state replaces the table, the filters stay")
    void emptyState() throws Exception {
        stubUsers(200, page(0, 0, 0));

        String main = mainOfAdminRequest("q", "nadie");

        assertThat(main).contains("No hay usuarios que coincidan").doesNotContain("<table")
                .doesNotContain("<caption").doesNotContain("Siguiente")
                .containsPattern("<input[^>]*name=\"q\"[^>]*value=\"nadie\"");
    }

    @ParameterizedTest(name = "API status {0}")
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 503})
    @DisplayName("an API failure degrades to a notice with status 503, keeps the filters and leaks nothing")
    void unavailableState(int apiStatus, CapturedOutput output) throws Exception {
        stubUsers(apiStatus, "{\"code\":\"SECRET_CODE\",\"detail\":\"secret-detail\"}");

        MvcResult result = adminRequest("role", "ADMIN", "q", "zq-needle");

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        String html = result.getResponse().getContentAsString();
        String main = mainOf(html);
        assertThat(main).contains("No pudimos cargar los usuarios en este momento")
                .containsPattern("role=\"alert\"").doesNotContain("<table").doesNotContain("No hay usuarios")
                .containsPattern("<input[^>]*name=\"q\"[^>]*value=\"zq-needle\"")
                .containsPattern("<a[^>]*href=\"/gestion/usuarios\"[^>]*>\\s*Usuarios\\s*</a>");
        assertThat(html).doesNotContain("SECRET_CODE").doesNotContain("secret-detail");
        assertThat(output.getAll()).doesNotContain("secret-detail").doesNotContain("zq-needle");
    }

    @Test
    @DisplayName("a body the BFF cannot use is the same notice")
    void malformedBody() throws Exception {
        stubUsers(200, "<html>not json</html>");

        MvcResult result = adminRequest();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(mainOf(result.getResponse().getContentAsString()))
                .contains("No pudimos cargar los usuarios en este momento");
    }

    // ---- pagination ----

    @Test
    @DisplayName("a single page has no pagination")
    void singlePageHasNoPagination() throws Exception {
        stubUsers(200, ONE_PAGE);

        assertThat(mainOfAdminRequest()).doesNotContain("Paginación").doesNotContain("Siguiente");
    }

    @Test
    @DisplayName("the first page has Siguiente as a link and Anterior as a disabled one")
    void firstPage() throws Exception {
        stubUsers(200, page(0, 3, 55, user("a@example.com", "A", "A", "STUDENT", "ACTIVE", "2026-10-01T09:30:00")));

        String main = mainOfAdminRequest();

        assertThat(main).containsPattern("<nav[^>]*aria-label=\"Paginación de usuarios\"")
                .containsPattern("aria-current=\"page\"[^>]*>\\s*Página 1 de 3\\s*<")
                .containsPattern("<a[^>]*rel=\"next\"[^>]*href=\"/gestion/usuarios\\?role=&amp;q=&amp;page=1\"[^>]*>"
                        + "\\s*Siguiente\\s*</a>")
                .containsPattern("<a[^>]*aria-disabled=\"true\"[^>]*>\\s*Anterior\\s*</a>")
                .doesNotContainPattern("<a[^>]*href=[^>]*>\\s*Anterior\\s*</a>");
    }

    @Test
    @DisplayName("a middle page links both ways and keeps the filters in both links")
    void middlePageKeepsTheFilters() throws Exception {
        stubUsers(200, page(1, 3, 55, user("a@example.com", "A", "A", "STUDENT", "ACTIVE", "2026-10-01T09:30:00")));

        String main = mainOfAdminRequest("role", "STUDENT", "q", "ada", "page", "1");

        assertThat(main).containsPattern("aria-current=\"page\"[^>]*>\\s*Página 2 de 3\\s*<")
                .containsPattern("<a[^>]*href=\"/gestion/usuarios\\?role=STUDENT&amp;q=ada&amp;page=0\"[^>]*>"
                        + "\\s*Anterior")
                .containsPattern("<a[^>]*href=\"/gestion/usuarios\\?role=STUDENT&amp;q=ada&amp;page=2\"[^>]*>"
                        + "\\s*Siguiente")
                .doesNotContain("aria-disabled");
    }

    @Test
    @DisplayName("the last page has Anterior as a link and Siguiente as a disabled one")
    void lastPage() throws Exception {
        stubUsers(200, page(2, 3, 55, user("a@example.com", "A", "A", "STUDENT", "ACTIVE", "2026-10-01T09:30:00")));

        String main = mainOfAdminRequest("page", "2");

        assertThat(main)
                .containsPattern("<a[^>]*rel=\"prev\"[^>]*href=\"/gestion/usuarios\\?role=&amp;q=&amp;page=1\"[^>]*>"
                        + "\\s*Anterior\\s*</a>")
                .containsPattern("<a[^>]*aria-disabled=\"true\"[^>]*>\\s*Siguiente\\s*</a>")
                .doesNotContainPattern("<a[^>]*href=[^>]*>\\s*Siguiente\\s*</a>");
    }

    @Test
    @DisplayName("pagination links re-encode the search text: it cannot add parameters or break the attribute")
    void paginationLinksEncodeTheText() throws Exception {
        stubUsers(200, page(0, 2, 30, user("a@example.com", "A", "A", "STUDENT", "ACTIVE", "2026-10-01T09:30:00")));

        String main = mainOfAdminRequest("q", "a&role=ADMIN&page=9\"><b> x");

        assertThat(main).contains("q=a%26role%3DADMIN%26page%3D9%22%3E%3Cb%3E%20x")
                .doesNotContain("role=ADMIN&amp;page=9\"><b>");
    }

    // ---- caching ----

    @Test
    @DisplayName("the page is never cached: it holds personal data")
    void notCached() throws Exception {
        stubUsers(200, ONE_PAGE);
        Cookie session = logIn("ADMIN");

        mockMvc.perform(get(PATH).cookie(session)).andExpect(header().string("Cache-Control", "no-store"));

        stubUsers(503, "{}");
        mockMvc.perform(get(PATH).cookie(session)).andExpect(header().string("Cache-Control", "no-store"));
    }

    // ---- privacy of the logs ----

    @Test
    @DisplayName("emails, names and the search text never reach the logs")
    void logsHoldNoPersonalData(CapturedOutput output) throws Exception {
        stubUsers(200, ONE_PAGE);

        adminRequest("q", "needle-in-the-search-box");

        assertThat(output.getAll()).doesNotContain("needle-in-the-search-box").doesNotContain("ada@example.com")
                .doesNotContain("Lovelace");
    }
}
