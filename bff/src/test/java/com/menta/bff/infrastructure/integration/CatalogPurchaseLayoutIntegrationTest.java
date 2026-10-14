package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Covers the catalog, learning and purchase pages migrated to the shared layout (#536, slice 3b):
 * each one renders the skip link, a single main landmark and the stylesheet, keeps its forms and
 * links, uses semantic markup and carries no inline styles.
 */
@DisplayName("Catalog and purchase views layout Integration Tests")
class CatalogPurchaseLayoutIntegrationTest extends BaseIntegrationTest {

    private static final String PHYSICAL_ID = "0b6f3a52-1111-4c3e-9a10-aaaaaaaaaaaa";
    private static final String VIRTUAL_ID = "course-1";
    private static final String QUOTE_ID = "00000000-0000-0000-0000-0000000000b1";
    private static final String PAYMENT_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String KEY = "00000000-0000-0000-0000-0000000000c1";
    private static final String STREAM_URL = "https://cdn.mentadance.test/stream/lesson-1.m3u8";
    private static final String MY_SESSIONS = "/api/v1/physical/sessions/me";
    private static final String QUOTES = "/api/v1/billing/physical/quotes";
    private static final String PURCHASES = "/api/v1/billing/physical/purchases";
    private static final String PAYMENT = "/api/v1/billing/payments/" + PAYMENT_ID;
    private static final String ACCESS_TOKEN = TestAccessTokens.withRole("STUDENT");

    private static final String PHYSICAL_DETAIL =
            """
            {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
             "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                          "capacity": 12,
                          "sessions": [
                            {"sessionId": "s-1", "scheduledAt": "2026-10-12T21:00:00Z", "capacity": 12,
                             "availableSpots": 5},
                            {"sessionId": "s-2", "scheduledAt": "2026-10-19T21:00:00Z", "capacity": 12,
                             "availableSpots": 0}]}}
            """.formatted(PHYSICAL_ID);

    private static final String CATALOG_LIST =
            """
            {"courses": [
              {"courseId": "course-1", "modality": "VIRTUAL", "title": "Ballet Básico", "level": "BEGINNER",
               "physical": null, "virtual": {"premium": false}},
              {"courseId": "%s", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
               "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                            "capacity": 12},
               "virtual": null}]}
            """.formatted(PHYSICAL_ID);

    private static final String VIRTUAL_DETAIL =
            """
            {"courseId": "course-1", "title": "Ballet Básico", "description": "Introducción al ballet clásico",
             "thumbnailUrl": "https://cdn/thumb.jpg", "category": "ballet", "level": "beginner", "isPremium": true,
             "modules": [{"moduleId": "module-1", "title": "Módulo 1", "order": 1, "lessons": [
               {"lessonId": "lesson-1", "title": "Postura básica", "duration": "05:00", "isFree": true, "order": 1},
               {"lessonId": "lesson-2", "title": "Giro avanzado", "duration": "08:00", "isFree": false,
                "order": 2}]}],
             "stats": {"moduleCount": 1, "lessonCount": 2, "totalDuration": "13m"}}
            """;

    private static final String PLANS =
            """
            {"plans": [
              {"id": "plan-1", "name": "Plan Mensual", "description": "Acceso completo por un mes",
               "price": 4999.00, "currency": "ARS", "durationDays": 30, "featured": false},
              {"id": "plan-2", "name": "Plan Anual", "description": "Acceso completo por un año",
               "price": 49990.00, "currency": "ARS", "durationDays": 365, "featured": true}]}
            """;

    private static final String LESSON =
            """
            {"lesson": {"lessonId": "lesson-1", "title": "Postura básica", "description": "Introducción",
                        "duration": "05:00", "order": 1, "videoId": null,
                        "course": {"courseId": "course-1", "title": "Ballet Básico"},
                        "module": {"moduleId": "module-1", "title": "Módulo 1"}},
             "navigation": {"previousLesson": null,
                            "nextLesson": {"lessonId": "lesson-2", "title": "Giro avanzado"}}}
            """;

    private static final String MY_SESSIONS_BODY =
            """
            {"from": "2026-10-13T03:00:00Z", "to": "2026-12-12T03:00:00Z", "sessions": [
              {"sessionId": "s-1", "courseId": "%s", "courseName": "Salsa en vivo", "instructorName": "María",
               "scheduledAt": "2026-10-12T21:00:00Z", "checkInOpen": false}]}
            """.formatted(PHYSICAL_ID);

    private static final String CHECKOUT_BODY =
            """
            {"paymentId": "%s", "quoteId": "%s", "status": "PENDING", "providerPreferenceId": "p",
             "checkoutUrl": "https://pay.example.com/checkout/1", "externalReference": "r",
             "bankTransferInstructions": null}
            """.formatted(PAYMENT_ID, QUOTE_ID);

    private static final String QUOTE_BODY =
            """
            {"id": "%s", "courseId": "%s", "purchaseType": "MONTHLY", "scheduledSessionCount": 4,
             "selectedSessionId": null, "amount": 12500.50, "currency": "ARS", "availability": "%s",
             "pricingVersion": 3, "createdAt": "2026-10-10T12:00:00Z", "expiresAt": "2026-10-10T13:00:00Z"}
            """;

    private static final String TRANSFER_BODY =
            """
            {"paymentId": "%s", "quoteId": "%s", "status": "PENDING", "providerPreferenceId": null,
             "checkoutUrl": null, "externalReference": "PHY-BT-1",
             "bankTransferInstructions": {"cbu": "0000003100010000000001", "alias": "menta.dance",
               "holder": "Menta Dance SRL", "cuit": "30-12345678-9",
               "amount": {"amount": 12500.50, "currency": "ARS"}, "reference": "PHY-BT-1"}}
            """.formatted(PAYMENT_ID, QUOTE_ID);

    private static final Pattern HEADING = Pattern.compile("<h([1-6])[\\s>]");

    private Cookie session;

    @BeforeEach
    void stubUpstream() {
        stubGet("/api/v1/catalog/courses", 200, CATALOG_LIST);
        stubGet("/api/v1/catalog/courses/" + PHYSICAL_ID, 200, PHYSICAL_DETAIL);
        stubGet("/api/v1/catalog/courses/" + VIRTUAL_ID, 200, VIRTUAL_DETAIL);
        stubGet("/api/v1/billing/physical/courses/" + PHYSICAL_ID + "/pricing", 200,
                "{\"courseId\": \"" + PHYSICAL_ID + "\", \"monthlyPrice\": 25000.00, \"currency\": \"ARS\"}");
        stubGet("/api/v1/billing/plans", 200, PLANS);
        stubGet("/api/v1/virtual/lessons/lesson-2", 403, "{}");
    }

    @ParameterizedTest(name = "{0} renderiza el layout común")
    @ValueSource(strings = {
        "/",
        "/?modality=virtual",
        "/classes",
        "/classes/" + PHYSICAL_ID,
        "/courses/" + VIRTUAL_ID,
        "/courses/" + VIRTUAL_ID + "/lessons/lesson-2",
        "/plans"})
    @DisplayName("las páginas públicas incluyen layout, encabezados en orden y ningún estilo inline")
    void publicPagesRenderTheCommonLayout(String path) throws Exception {
        String html = body(get(path));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
    }

    @Test
    @DisplayName("el inicio conserva título, filtros por modalidad y enlaces al detalle")
    void homeKeepsItsContent() throws Exception {
        String html = body(get("/"));

        assertThat(html)
                .contains("<title>Inicio - Menta Dance</title>")
                .contains("href=\"/courses/course-1\"")
                .contains("href=\"/classes/" + PHYSICAL_ID + "\"")
                .contains("href=\"/login\"")
                .contains("href=\"/registro\"")
                .contains("href=\"/classes\"")
                .contains("href=\"/?modality=virtual\"")
                .contains("aria-label=\"Filtrar por modalidad\"");
        assertThat(html).containsPattern("<strong[^>]*>\\s*Todos\\s*</strong>");
    }

    @Test
    @DisplayName("el listado de clases usa lista semántica con enlaces a cada detalle")
    void classesListIsSemantic() throws Exception {
        String html = body(get("/classes"));

        assertThat(html).containsPattern("(?s)<ul[^>]*>.*<li[^>]*>.*Salsa en vivo.*</li>.*</ul>")
                .contains("href=\"/classes/" + PHYSICAL_ID + "\"")
                .contains("<title>Clases presenciales - Menta Dance</title>");
    }

    @Test
    @DisplayName("el detalle de clase anónimo muestra sesiones en lista, estados con texto e invita a ingresar")
    void classDetailAnonymous() throws Exception {
        String html = body(get("/classes/" + PHYSICAL_ID));

        assertThat(html).contains("<title>Salsa en vivo - Menta Dance</title>")
                .containsPattern("(?s)<ul[^>]*>.*5 lugares libres.*Completo.*</ul>")
                .contains("Ingresá para comprar")
                .contains("href=\"/login\"")
                .doesNotContain("<form");
    }

    @Test
    @DisplayName("el detalle de curso conserva enlaces de lecciones y marcas Gratis/Restringida")
    void courseDetailKeepsLessons() throws Exception {
        String html = body(get("/courses/" + VIRTUAL_ID));

        assertThat(html).contains("<title>Ballet Básico - Menta Dance</title>")
                .contains("href=\"/courses/course-1/lessons/lesson-1\"")
                .contains("href=\"/courses/course-1/lessons/lesson-2\"")
                .contains("Gratis").contains("Restringida")
                .contains("Comenzar")
                .contains("1 módulos");
    }

    @Test
    @DisplayName("la lección sin acceso muestra la invitación a los planes sin video")
    void lockedLessonKeepsPlansLink() throws Exception {
        String html = body(get("/courses/" + VIRTUAL_ID + "/lessons/lesson-2"));

        assertThat(html).contains("href=\"/plans\"").contains("Esta lección requiere una suscripción activa.")
                .doesNotContain("<video").doesNotContain(".m3u8");
    }

    @Test
    @DisplayName("la lección reproducible mantiene el video con su URL dentro de un contenedor con proporción")
    void playableLessonKeepsTheVideoInAResponsiveBox() throws Exception {
        stubGet("/api/v1/virtual/lessons/lesson-1", 200, LESSON);
        stubGet("/api/v1/virtual/lessons/lesson-1/stream", 200,
                "{\"stream\": {\"url\": \"" + STREAM_URL + "\", \"expiresAt\": \"2026-01-01T00:00:00Z\"}}");

        String html = body(get("/courses/" + VIRTUAL_ID + "/lessons/lesson-1"));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
        assertThat(html).contains("<video controls src=\"" + STREAM_URL + "\"")
                .containsPattern("(?s)<div[^>]*aspect-video[^>]*>\\s*<video")
                .contains("aria-label=\"Video de la lección: Postura básica\"")
                .contains("preload=\"metadata\"")
                .containsPattern("(?s)<video[^>]*>[^<]*No se puede reproducir este video[^<]*</video>")
                .contains("href=\"/courses/course-1/lessons/lesson-2\"");
    }

    @Test
    @DisplayName("los planes se listan con su destacado como texto y precio")
    void plansAreAList() throws Exception {
        String html = body(get("/plans"));

        assertThat(html).containsPattern("(?s)<ul[^>]*>.*Plan Mensual.*Plan Anual.*</ul>")
                .contains("Destacado")
                .contains("cada 30 días")
                .contains("4.999,00 ARS");
    }

    @Test
    @DisplayName("las sesiones propias llevan un badge con texto 'Ya tenés lugar' y sin botón de cotizar")
    void ownedSessionsGetABadge() throws Exception {
        logIn();
        stubGet(MY_SESSIONS, 200, MY_SESSIONS_BODY);

        String html = body(get("/classes/" + PHYSICAL_ID).cookie(session));

        assertCommonLayout(html);
        assertThat(html)
                .containsPattern("<[a-z]+ class=\"[^\"]*badge badge-success[^\"]*\"[^>]*>\\s*Ya tenés lugar\\s*<")
                .doesNotContain("name=\"selectedSessionId\" value=\"s-1\"")
                .contains("Cotizar clases del mes");
    }

    @Test
    @DisplayName("/dashboard usa el layout y conserva el cierre de sesión con CSRF")
    void dashboardKeepsLogout() throws Exception {
        logIn();

        String html = body(get("/dashboard").cookie(session));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
        assertThat(html).contains("action=\"/logout\"")
                .contains("name=\"_csrf\"")
                .contains("href=\"/classes\"")
                .contains("<title>Panel - Menta Dance</title>");
    }

    @Test
    @DisplayName("la cotización usa el layout y conserva sus formularios, claves y CSRF")
    void quoteKeepsItsForms() throws Exception {
        logIn();
        stubQuote("AVAILABLE");

        String html = body(quoteRequest().cookie(session));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
        assertThat(html).contains("action=\"/compras\"")
                .contains("action=\"/compras/transferencia\"")
                .contains("name=\"quoteId\" value=\"" + QUOTE_ID + "\"")
                .contains("name=\"idempotencyKey\"")
                .contains("name=\"_csrf\"")
                .contains("Pagar con Mercado Pago")
                .contains("Pagar por transferencia bancaria")
                .contains("href=\"/classes/" + PHYSICAL_ID + "\"");
    }

    @Test
    @DisplayName("una cotización sin lugares avisa con role=alert y no ofrece pagar")
    void unavailableQuoteIsAnAlert() throws Exception {
        logIn();
        stubQuote("UNAVAILABLE");

        String html = body(quoteRequest().cookie(session));

        assertCommonLayout(html);
        assertThat(html).contains("role=\"alert\"")
                .contains("Sin lugares disponibles")
                .doesNotContain("action=\"/compras\"");
    }

    @Test
    @DisplayName("la transferencia usa el layout, lista los datos y conserva el formulario multipart del comprobante")
    void transferKeepsTheProofForm() throws Exception {
        logIn();
        stubQuote("AVAILABLE");
        stubTransfer();
        mockMvc.perform(post("/compras/transferencia").cookie(session).with(csrf())
                .param("quoteId", QUOTE_ID).param("courseId", PHYSICAL_ID).param("idempotencyKey", KEY));

        String html = body(get("/compras/transferencia").cookie(session));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
        assertThat(html).contains("0000003100010000000001", "menta.dance", "PHY-BT-1")
                .contains("enctype=\"multipart/form-data\"")
                .contains("action=\"/compras/transferencia/comprobante\"")
                .contains("name=\"paymentId\" value=\"" + PAYMENT_ID + "\"")
                .contains("name=\"_csrf\"")
                .containsPattern("<input[^>]*type=\"file\"[^>]*name=\"file\"")
                .contains("accept=\"image/png,image/jpeg,application/pdf\"")
                .containsPattern("<label[^>]*for=\"proof-file\"");
    }

    @Test
    @DisplayName("el aviso de comprobante recibido usa el layout y un alert con role=status")
    void proofNoticeIsAStatus() throws Exception {
        logIn();
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(PAYMENT + "/proof")).willReturn(aResponse().withStatus(200)));

        String html = body(multipart("/compras/transferencia/comprobante")
                .file(new MockMultipartFile("file", "recibo.png", "image/png", new byte[] {1, 2, 3}))
                .param("paymentId", PAYMENT_ID).cookie(session).with(csrf()));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
        assertThat(html).contains("Recibimos tu comprobante").contains("role=\"status\"")
                .contains("href=\"/classes\"");
    }

    @Test
    @DisplayName("un fallo de compra usa el layout y muestra el mensaje con role=alert")
    void purchaseFailureIsAnAlert() throws Exception {
        logIn();
        stubQuote("AVAILABLE");
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":409,\"code\":\"CAPACITY_UNAVAILABLE\"}")));

        String html = body(post("/compras").cookie(session).with(csrf())
                .param("quoteId", QUOTE_ID).param("courseId", PHYSICAL_ID).param("idempotencyKey", KEY));

        assertCommonLayout(html);
        assertThat(html).contains("role=\"alert\"").contains("Ya no hay lugares")
                .contains("href=\"/classes/" + PHYSICAL_ID + "\"");
    }

    @Test
    @DisplayName("el retorno de un pago confirmado usa el layout y lista las sesiones asignadas")
    void confirmedReturnListsAssignedSessions() throws Exception {
        logIn();
        stubQuote("AVAILABLE");
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(CHECKOUT_BODY)));
        mockMvc.perform(post("/compras").cookie(session).with(csrf())
                .param("quoteId", QUOTE_ID).param("courseId", PHYSICAL_ID).param("idempotencyKey", KEY));
        stubGet(PAYMENT, 200, "{\"status\":\"COMPLETED\",\"createdAt\":\"2026-10-10T12:00:00Z\","
                + "\"updatedAt\":\"2026-10-10T12:01:00Z\"}");
        stubGet(MY_SESSIONS, 200, MY_SESSIONS_BODY);

        String html = body(get("/compras/retorno").cookie(session));

        assertCommonLayout(html);
        assertHeadingsInOrder(html);
        assertThat(html).contains("id=\"assigned-sessions\"")
                .contains("Tus próximas sesiones")
                .containsPattern("(?s)<ul[^>]*>.*Salsa en vivo.*</ul>")
                .contains("href=\"/classes\"");
    }

    private void logIn() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(ACCESS_TOKEN))));
        session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
    }

    private MockHttpServletRequestBuilder quoteRequest() {
        return post("/classes/" + PHYSICAL_ID + "/cotizar").with(csrf()).param("purchaseType", "MONTHLY");
    }

    private static void stubQuote(String availability) {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(QUOTES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(QUOTE_BODY.formatted(QUOTE_ID, PHYSICAL_ID, availability))));
    }

    private static void stubTransfer() {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo(PURCHASES)).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(TRANSFER_BODY)));
    }

    private static void stubGet(String url, int status, String body) {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo(url)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    private static void assertCommonLayout(String html) {
        assertThat(html)
                .contains("href=\"#main\"")
                .contains("Saltar al contenido")
                .contains("<header")
                .contains("<footer")
                .containsPattern("<link[^>]*rel=\"stylesheet\"[^>]*href=\"/css/app-[0-9a-f]{32}\\.css\"")
                .contains("lang=\"es\"")
                .contains("<main id=\"main\"")
                .doesNotContain("<style")
                .doesNotContain(" style=\"");
        assertThat(html.split("<main", -1)).as("single main landmark").hasSize(2);
        assertThat(html.split("<h1", -1)).as("single h1").hasSize(2);
        assertThat(html).as("skip link target is focusable").contains("<main id=\"main\" tabindex=\"-1\"");
        assertThat(html).as("every nav landmark is named")
                .doesNotContainPattern("<nav(?![^>]*aria-label=)[^>]*>");
    }

    /** The first heading is an h1 and no level is skipped when going deeper. */
    private static void assertHeadingsInOrder(String html) {
        List<Integer> levels = new ArrayList<>();
        Matcher heading = HEADING.matcher(html);
        while (heading.find()) {
            levels.add(Integer.parseInt(heading.group(1)));
        }
        assertThat(levels).isNotEmpty();
        assertThat(levels.get(0)).as("first heading").isEqualTo(1);
        for (int i = 1; i < levels.size(); i++) {
            assertThat(levels.get(i) - levels.get(i - 1))
                    .as("heading jump at %d in %s", i, levels).isLessThanOrEqualTo(1);
        }
    }
}
