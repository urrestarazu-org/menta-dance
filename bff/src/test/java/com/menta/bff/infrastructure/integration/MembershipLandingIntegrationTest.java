package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.menta.bff.application.usecase.GetMembershipLandingViewUseCase;
import com.menta.bff.application.usecase.MembershipLandingView;
import com.menta.bff.domain.membership.MembershipPriceSchedule;
import com.menta.bff.domain.membership.PriceRow;
import com.menta.bff.domain.membership.PriceTier;
import com.menta.bff.infrastructure.config.MembershipLandingProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The temporary membership landing (#685) rendered through the real Thymeleaf templates, security chain and
 * layout. The tiers are the real ones from application.yml; the date is fixed by stubbing the use case, so the
 * table of any day can be checked. Pay links are fake {@code pay.example.test} ones.
 */
@DisplayName("Membership landing Integration Tests")
@TestPropertySource(properties = {
    "menta.landing.membership.enabled=true",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_01=https://pay.example.test/plan-1",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_02=https://pay.example.test/plan-2",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_03=https://pay.example.test/plan-3",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_04=https://pay.example.test/plan-4",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_05=https://pay.example.test/plan-5",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_06=https://pay.example.test/plan-6",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_07=https://pay.example.test/plan-7",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_08=https://pay.example.test/plan-8",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_09=https://pay.example.test/plan-9",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_10=https://pay.example.test/plan-10",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_11=https://pay.example.test/plan-11",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_12=https://pay.example.test/plan-12",
    "MENTA_LANDING_MEMBERSHIP_PAY_URL_13=https://pay.example.test/plan-13"
})
class MembershipLandingIntegrationTest extends BaseIntegrationTest {

    private static final String SERVER_NOW = "2026-11-01T15:30:00Z";

    @MockitoBean
    private GetMembershipLandingViewUseCase useCase;

    @Autowired
    private MembershipLandingProperties properties;

    private String landingOn(LocalDate today) throws Exception {
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(properties.toPriceTiers(), today);
        when(useCase.execute()).thenReturn(
                new MembershipLandingView(rows, MembershipPriceSchedule.isSalesClosed(rows), SERVER_NOW));
        return mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long count(String text, String fragment) {
        return Pattern.compile(Pattern.quote(fragment)).matcher(text).results().count();
    }

    @Test
    @DisplayName("shows the 13 tiers with installment, installments and total, and the two current ones payable")
    void rendersThePriceTableOnTheFirstDay() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(count(main, "data-price-row")).isEqualTo(13);
        assertThat(main).contains("Asistencia perfecta 2026", "$ 25.000", "$ 300.000", "$ 312.000", "$ 374.000",
                "$ 120.000", "$ 80.000", "diciembre bonificada");
        assertThat(count(main, "data-pay-button")).isEqualTo(2);
        assertThat(main).contains("href=\"https://pay.example.test/plan-1\"")
                .contains("href=\"https://pay.example.test/plan-2\"")
                .doesNotContain("pay.example.test/plan-3\"");
        assertThat(main).doesNotContain("Finalizado");
    }

    @Test
    @DisplayName("shows the countdown to the current price, corrected with the server clock")
    void rendersTheCountdown() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("data-promo-countdown")
                .contains("data-expires-at=\"2026-11-08T03:00:00Z\"")
                .contains("data-server-now=\"" + SERVER_NOW + "\"")
                .contains("Esta promoción termina el 07/11/2026");
    }

    @Test
    @DisplayName("once a deadline passes its rows are struck through and marked Finalizado, the next price is payable")
    void expiredRowsAreMarked() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 10)));

        assertThat(count(main, "Finalizado")).isEqualTo(2);
        assertThat(main).contains("line-through");
        assertThat(count(main, "data-pay-button")).isEqualTo(1);
        assertThat(main).contains("href=\"https://pay.example.test/plan-3\"")
                .contains("data-expires-at=\"2026-11-17T03:00:00Z\"");
    }

    @Test
    @DisplayName("after the last deadline there is no pay button nor countdown, and the page says sales are over")
    void salesClosed() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2027, 10, 12)));

        assertThat(count(main, "data-pay-button")).isZero();
        assertThat(main).doesNotContain("data-promo-countdown")
                .contains("El período de inscripción a la membresía finalizó.");
        assertThat(count(main, "Finalizado")).isEqualTo(13);
    }

    @Test
    @DisplayName("tells what the membership includes with the schedules of the membership document")
    void describesTheMembership() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("Menta Eventos", "Menta Online", "Menta Intensivos", "Menta Clases Regulares")
                .contains("Lunes de 19:30 a 21:00")
                .contains("últimos dos domingos")
                .contains("18:30");
    }

    @Test
    @DisplayName("states the payment conditions of the document and nothing the document does not say")
    void statesTheConditions() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("suscripción de Mercado Pago", "débito automático",
                "La membresía comienza en enero de 2027", "julio y diciembre",
                "se da de baja y finaliza automáticamente");
        assertThat(main).doesNotContain("comprobante").doesNotContain("Guadarrama");
    }

    @Test
    @DisplayName("keeps to the CSP: no external hosts, no inline script, a hashed same-origin script with defer")
    void respectsTheContentSecurityPolicy() throws Exception {
        String html = landingOn(LocalDate.of(2026, 11, 1));

        assertThat(html).doesNotContain("fonts.googleapis.com", "fonts.gstatic.com", "Material Symbols");
        assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
        Matcher script = Pattern.compile("<script[^>]*src=\"(/js/promo-countdown-[0-9a-f]{32}\\.js)\"[^>]*defer")
                .matcher(html);
        assertThat(script.find()).as("hashed countdown script with defer").isTrue();
        assertThat(html).doesNotContainPattern("\\sstyle=\"");
    }

    @Test
    @DisplayName("uses the common layout: one h1, main landmark, skip link, title and the site header and footer")
    void usesTheCommonLayout() throws Exception {
        String html = landingOn(LocalDate.of(2026, 11, 1));

        assertThat(html).contains("<title>Membresía Full Pass Menta - Menta Dance</title>")
                .contains("href=\"#main\"").contains("<main id=\"main\"").contains("<header").contains("<footer")
                .contains("lang=\"es\"");
        assertThat(count(html, "<h1")).isEqualTo(1);
    }

    @Test
    @DisplayName("a tier in force without its link shows \"Link de pago próximamente\" and no pay button")
    void currentTierWithoutLinkShowsComingSoon() throws Exception {
        List<PriceTier> tiers = List.of(
                new PriceTier("Sábado 7 de noviembre", 26_000, 12, LocalDate.of(2026, 11, 7), "", false));
        List<PriceRow> rows = MembershipPriceSchedule.rowsAt(tiers, LocalDate.of(2026, 11, 1));
        when(useCase.execute()).thenReturn(new MembershipLandingView(rows, false, SERVER_NOW));

        String main = HtmlSections.mainOf(mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(main).contains("Link de pago próximamente");
        assertThat(count(main, "data-pay-button")).isZero();
    }

    @Test
    @DisplayName("keeps only the brand in the header: no navigation, no account links, no mobile menu")
    void headerHasOnlyTheBrand() throws Exception {
        String html = landingOn(LocalDate.of(2026, 11, 1));
        String header = html.substring(html.indexOf("<header"), html.indexOf("</header>"));

        assertThat(header).contains("Menta Dance")
                .doesNotContain("<nav")
                .doesNotContain("Clases presenciales")
                .doesNotContain("Planes")
                .doesNotContain("Iniciar sesión")
                .doesNotContain("Crear cuenta")
                .doesNotContain("<details")
                .doesNotContain("/login")
                .doesNotContain("/registro");
    }

    @Test
    @DisplayName("keeps only the brand and the legal information in the footer: no site navigation or account links")
    void footerHasNoNavigation() throws Exception {
        String html = landingOn(LocalDate.of(2026, 11, 1));
        String footer = html.substring(html.indexOf("<footer"), html.indexOf("</footer>"));

        assertThat(footer).contains("Menta Dance").contains("Información legal")
                .contains("Términos y condiciones").contains("Política de devolución").contains("Contacto")
                .doesNotContain("Explorar")
                .doesNotContain("Tu cuenta")
                .doesNotContain("Clases presenciales")
                .doesNotContain("Planes")
                .doesNotContain("Iniciar sesión")
                .doesNotContain("Crear cuenta")
                .doesNotContain("Pie de página")
                .doesNotContain("/login")
                .doesNotContain("/registro")
                .doesNotContain("/classes")
                .doesNotContain("/plans");
    }

    @Test
    @DisplayName("renders the price table as a table with column headers")
    void priceTableIsAccessible() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("<caption")
                .containsPattern("<th scope=\"col\"[^>]*>\\s*Alta hasta")
                .containsPattern("<th scope=\"col\"[^>]*>\\s*Cuota mensual")
                .containsPattern("<th scope=\"col\"[^>]*>\\s*Cuotas")
                .containsPattern("<th scope=\"col\"[^>]*>\\s*Total");
    }

    @Test
    @DisplayName("serving the landing does not call the catalog upstream, and the rest of the site is untouched")
    void doesNotTouchTheCatalogNorTheRestOfTheSite() throws Exception {
        landingOn(LocalDate.of(2026, 11, 1));

        WIRE_MOCK_SERVER.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/catalog/courses")));
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/contacto")).andExpect(status().isOk());
        mockMvc.perform(get("/registro")).andExpect(status().isOk());
    }
}
