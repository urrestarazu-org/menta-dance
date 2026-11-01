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
    @DisplayName("states the payment conditions of the document, plus the receipt reminder the owner asked for")
    void statesTheConditions() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("suscripción de Mercado Pago", "débito automático",
                "La membresía comienza en enero de 2027", "julio y diciembre",
                "se da de baja y finaliza automáticamente");
        // The owner added the receipt reminder after the document; the mock-up's address stays out.
        assertThat(main).contains("comprobante").doesNotContain("Guadarrama");
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

    private static List<String> tagsOf(String html, String regex) {
        return Pattern.compile(regex).matcher(html).results().map(match -> match.group()).toList();
    }

    @Test
    @DisplayName("the hero presents one membership with all the proposals of Menta")
    void heroPresentsAllTheProposals() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("Una sola membresía. Todas las propuestas de Menta.")
                .doesNotContain("Todo Menta");
    }

    @Test
    @DisplayName("the hero says the year of bachata runs from January to November")
    void heroSaysTheYearRunsFromJanuaryToNovember() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("Todas las propuestas de Menta. Un año entero de bachata sensual y tradicional, "
                + "desde enero a noviembre")
                .doesNotContain("tradicional, de enero a noviembre");
    }

    @Test
    @DisplayName("the hero sells the price without quoting an amount that changes with the date")
    void heroSellsThePriceWithoutAFigure() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));
        String lead = main.substring(main.indexOf("hero-lead"), main.indexOf("</p>", main.indexOf("hero-lead")));

        assertThat(lead)
                .contains("te anotás una sola vez")
                .contains("¡y a un precio absurdo!")
                .doesNotContain("parece una locura")
                .doesNotContain("sin tener que decidir cada mes si te anotás o no")
                .doesNotContain("$");
    }

    @Test
    @DisplayName("the hero presents the July and December recess as time to recharge and balance dance with life")
    void heroPresentsTheRecessAsTimeToRecharge() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("Incluye clases regulares, eventos, intensivos y clases online, con receso en "
                + "julio y diciembre para recargar energías y equilibrar tus metas de baile con tu vida personal.");
    }

    @Test
    @DisplayName("the value block says the membership adds every event of the year to the individual formats")
    void valueBlockMentionsEveryEventOfTheYear() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("La membresía incluye todo eso más todos los eventos del año, en una sola cuota "
                        + "mensual: te anotás una vez y bailás todo el año.")
                .doesNotContain("y mucho más");
    }

    @Test
    @DisplayName("the events block says the Saturday falls between the 10th and the 20th of each month")
    void eventsBlockStatesTheDayOfEachMonth() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main)
                .contains("Un sábado por mes, entre el 10 y el 20 de cada mes, de 19 a 21 hs, de enero a octubre")
                .doesNotContain("entre el 10 y el 20, de 19 a 21 hs");
    }

    @Test
    @DisplayName("the regular classes block promises a gradual, deep training that builds confidence")
    void regularClassesBlockPromisesConfidence() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main)
                .contains("Lunes de 19:30 a 21:00 hs., de abril a noviembre. Una formación paulatina y profunda, "
                        + "ideal para reforzar bases y ganar seguridad en cada clase.")
                .doesNotContain("entender lo que hacés");
    }

    @Test
    @DisplayName("the payment condition says the automatic debit happens on the 16th of each month")
    void paymentConditionStatesTheDebitDay() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("El pago se realiza únicamente mediante suscripción de Mercado Pago, con "
                        + "débito automático mensual los días 16 de cada mes.")
                .doesNotContain("desde que aceptás el link");
    }

    @Test
    @DisplayName("the start date and the first debit are two separate conditions")
    void startAndFirstDebitAreSeparateConditions() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main)
                .contains("<li>La membresía comienza en enero de 2027, aunque te suscribas antes.</li>")
                .contains("<li>El primer débito se hace el primer día 16 que sigue a tu alta en Mercado Pago, "
                        + "y después los 16 de cada mes.</li>")
                .doesNotContain("Se empezará a cobrar");
    }

    @Test
    @DisplayName("the conditions ask for the receipt and the Mercado Pago email, and say where to send them")
    void conditionsRemindToAttachTheReceipt() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("<li>No te olvides de adjuntar el comprobante o la captura del pago realizado, "
                + "junto con el mail con el que te registraste en Mercado Pago. Enviáselos a la Profe Lina: "
                + "sus datos están en <a href=\"/contacto\">contacto</a>.</li>");
    }

    @Test
    @DisplayName("the events block sells new choreographies, photos and sharing with women who love to dance")
    void eventsBlockSellsTheExperience() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main)
                .contains("diez encuentros para desafiarte con nuevas coreografías, lucir tu mejor outfit, "
                        + "sacarte fotos, disfrutar y compartir con mujeres que también aman bailar.")
                .doesNotContain("diez encuentros para aprender")
                .doesNotContain("compartir con la comunidad");
    }

    @Test
    @DisplayName("the online block writes the time as \"18:30 hs.\"")
    void onlineBlockWritesTheTimeWithHs() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main).contains("Los últimos dos domingos de cada mes, a las 18:30 hs., de abril a noviembre.")
                .doesNotContain("a las 18:30, de abril");
    }

    @Test
    @DisplayName("the intensive block writes both schedules with \"hs.\"")
    void intensiveBlockWritesTheTimesWithHs() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(main)
                .contains("Durante enero, febrero y marzo, presenciales: lunes de 19:30 a 21:00 hs. y miércoles "
                        + "de 19:30 a 21:30 hs. Cada curso intensivo dura un mes")
                .doesNotContain("21:00 y miércoles");
    }

    @Test
    @DisplayName("the blocks lift when the pointer is over them")
    void blocksLiftOnHover() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        // 4 formats + "Por qué conviene" + conditions
        assertThat(count(main, "hover-lift")).isEqualTo(6);
    }

    @Test
    @DisplayName("every price row changes tint smoothly; the rows in force also show an accent bar")
    void rowsHighlightOnHover() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        List<String> rows = tagsOf(main, "<tr\\b[^>]*data-price-row[^>]*>");
        assertThat(rows).hasSize(13).allSatisfy(row -> assertThat(row)
                .contains("transition-colors").containsPattern("(^|[\\s\"])group[\\s\"]"));
        // Locked rows (11 on Nov 1): grey; rows in force: the exclusive one in bright green, the other in light green
        assertThat(count(main, "hover:bg-gray-200")).isEqualTo(11);
        assertThat(count(main, "hover:bg-brand-bright/40")).isEqualTo(1);
        assertThat(count(main, "hover:bg-brand-light/50")).isEqualTo(1);
        // The accent bar only appears on the two rows in force
        assertThat(count(main, "group-hover:border-brand-action")).isEqualTo(2);
    }

    @Test
    @DisplayName("the pay button grows, darkens, moves its arrow and pulses a ring that stops on hover and focus")
    void payButtonGivesFeedback() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        List<String> buttons = tagsOf(main, "<a[^>]*data-pay-button[^>]*>");
        assertThat(buttons).hasSize(2).allSatisfy(tag -> assertThat(tag).contains("hover-pop").contains("group/pay"));
        assertThat(count(main, "animate-pay-ring")).isEqualTo(2);
        assertThat(count(main, "group-hover/pay:hidden")).isEqualTo(2);
        assertThat(count(main, "group-focus-visible/pay:hidden")).isEqualTo(2);
        assertThat(count(main, "motion-reduce:animate-none")).isEqualTo(2);
        assertThat(count(main, "group-hover/pay:translate-x-1")).isEqualTo(2);
    }

    @Test
    @DisplayName("the \"Ver precios\" buttons also react to the pointer")
    void seePricesButtonsReact() throws Exception {
        String main = HtmlSections.mainOf(landingOn(LocalDate.of(2026, 11, 1)));

        assertThat(tagsOf(main, "<a[^>]*href=\"#precios\"[^>]*>")).hasSize(2)
                .allSatisfy(tag -> assertThat(tag).contains("hover-pop"));
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
