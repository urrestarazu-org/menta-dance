package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Colour contrast of the compiled stylesheet (#529, slice 3). The WCAG 2.1 relative-luminance formula is applied to
 * the design tokens and to the rules that paint text, borders and focus rings over the dark surfaces and the hero
 * photo, so a palette or CSS change that drops a pair below AA fails the build. It reads the real compiled
 * {@code /static/css/app.css}; it needs no Spring context.
 */
@DisplayName("Stylesheet contrast Tests")
class StylesheetContrastTest {

    private static final double TEXT_MINIMUM = 4.5;
    private static final double NON_TEXT_MINIMUM = 3.0;
    private static final Pattern TOKEN = Pattern.compile("--color-([a-z-]+):(\\d+) (\\d+) (\\d+)");
    private static final Pattern RULE = Pattern.compile("(?:^|[}{])([^{}]+)\\{([^{}]*)}");
    private static final Pattern COLOR_VALUE = Pattern.compile(
            "rgb\\((?:var\\(--color-([a-z-]+)\\)|(\\d+) (\\d+) (\\d+))/(var\\(--tw-[a-z-]+\\)|[\\d.]+)\\)");
    private static final Pattern GRADIENT = Pattern.compile(
            "linear-gradient\\(#([0-9a-f]{6})([0-9a-f]{2}),#([0-9a-f]{6})([0-9a-f]{2})\\)");

    private static String css;
    private static final Map<String, int[]> TOKENS = new HashMap<>();

    @BeforeAll
    static void loadCompiledStylesheet() throws IOException {
        try (InputStream in = StylesheetContrastTest.class.getResourceAsStream("/static/css/app.css")) {
            assertThat(in).as("the compiled /static/css/app.css is on the classpath").isNotNull();
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher token = TOKEN.matcher(css);
        while (token.find()) {
            TOKENS.put(token.group(1), new int[] {
                Integer.parseInt(token.group(2)), Integer.parseInt(token.group(3)), Integer.parseInt(token.group(4))});
        }
        TOKENS.put("white", new int[] {255, 255, 255});
        TOKENS.put("gray-50", new int[] {249, 250, 251});
    }

    @ParameterizedTest(name = "{0} sobre {1} llega a {2}:1")
    @CsvSource({
        "brand-deep, brand-surface, 4.5",
        "brand-deep, white, 4.5",
        "brand-deep, gray-50, 4.5",
        "brand-action, white, 4.5",
        "brand-action, brand-surface, 4.5",
        "brand-action, feedback-success-surface, 4.5",
        "brand-action, feedback-error-surface, 4.5",
        "brand-action, feedback-warning-surface, 4.5",
        "white, brand-action, 4.5",
        "white, brand-deep, 4.5",
        "brand-deep, brand-light, 4.5",
        "brand-deep, brand-bright, 4.5",
        "brand-light, brand-deep, 4.5",
        "brand-surface, brand-deep, 4.5",
        "feedback-error, feedback-error-surface, 4.5",
        "feedback-error, white, 4.5",
        "feedback-error, brand-surface, 4.5",
        "feedback-success, feedback-success-surface, 4.5",
        "feedback-warning, feedback-warning-surface, 4.5"
    })
    @DisplayName("cada par texto/fondo de los tokens supera el mínimo AA de texto")
    void textPairsReachAa(String foreground, String background, double minimum) {
        assertThat(contrast(token(foreground), token(background))).isGreaterThanOrEqualTo(minimum);
    }

    @ParameterizedTest(name = "{0} sobre {1} llega a 3:1")
    @CsvSource({
        "brand-action, white",
        "brand-action, brand-surface",
        "brand-action, feedback-success-surface",
        "brand-action, feedback-error-surface",
        "brand-action, feedback-warning-surface",
        "brand-light, brand-deep",
        "white, brand-deep"
    })
    @DisplayName("el indicador de foco y los bordes de componentes superan 3:1 sobre cada superficie")
    void nonTextPairsReachThree(String foreground, String background) {
        assertThat(contrast(token(foreground), token(background))).isGreaterThanOrEqualTo(NON_TEXT_MINIMUM);
    }

    @ParameterizedTest(name = "{0} dibuja el foco en brand-light")
    @CsvSource({".site-header", ".site-footer", ".hero", ".auth-aside"})
    @DisplayName("sobre las superficies oscuras el foco usa brand-light: el verde de acción no llega a 3:1")
    void darkSurfacesUseALightFocusRing(String surface) {
        assertThat(contrast(token("brand-action"), token("brand-deep")))
                .as("the default focus colour on brand-deep").isLessThan(NON_TEXT_MINIMUM);

        String declarations = declarations(surface + " :focus-visible").orElse("");

        assertThat(declarations).contains("outline-color:rgb(var(--color-brand-light)/1)");
    }

    @Test
    @DisplayName("el degradé del hero deja el texto claro en 4,5:1 o más incluso sobre una foto blanca")
    void heroOverlayKeepsTextAtAaOverTheWorstPhoto() {
        Matcher gradient = GRADIENT.matcher(declarations(".hero").orElse(""));
        int found = 0;
        while (gradient.find()) {
            found++;
            int[] overlay = hex(gradient.group(1));
            double lightest = Math.min(alpha(gradient.group(2)), alpha(gradient.group(4)));

            int[] worstBackground = blend(overlay, lightest, token("white"));

            assertThat(contrast(token("brand-surface"), worstBackground)).as("hero text")
                    .isGreaterThanOrEqualTo(TEXT_MINIMUM);
            assertThat(contrast(token("brand-light"), worstBackground)).as("hero outline button and link")
                    .isGreaterThanOrEqualTo(TEXT_MINIMUM);
        }
        assertThat(found).as("the desktop and the mobile hero both paint a two-stop gradient over the photo")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("el botón de contorno claro, al pasar el mouse, pinta un relleno sólido en vez de aclarar la foto")
    void outlineButtonHoverDoesNotDependOnThePhoto() {
        int[] fill = colorOf(".btn-outline-light:hover", "background-color", token("brand-deep"));
        int[] text = colorOf(".btn-outline-light:hover", "color", token("brand-deep"));

        assertThat(contrast(text, fill)).isGreaterThanOrEqualTo(TEXT_MINIMUM);
    }

    @Test
    @DisplayName("el borde de los campos de las pantallas de cuenta se distingue del fondo con 3:1")
    void authInputBorderIsVisible() {
        int[] border = colorOf(".auth-body .field-input", "border-color", token("white"));
        int[] fill = colorOf(".auth-body .field-input", "background-color", token("white"));

        assertThat(contrast(border, token("white"))).as("border against the white card")
                .isGreaterThanOrEqualTo(NON_TEXT_MINIMUM);
        assertThat(contrast(border, fill)).as("border against the field fill").isGreaterThanOrEqualTo(NON_TEXT_MINIMUM);
    }

    @Test
    @DisplayName("los enlaces de las pantallas de cuenta siguen subrayados: el color solo no basta (1.4.1)")
    void authLinksStayUnderlined() {
        assertThat(contrast(token("brand-action"), token("brand-deep")))
                .as("link against surrounding text").isLessThan(NON_TEXT_MINIMUM);

        assertThat(declarations(".auth-body a").orElse("")).doesNotContain("text-decoration-line:none");
        assertThat(declarations(".auth-body a:hover")).isEmpty();
    }

    @Test
    @DisplayName("el borde de alert-info se distingue de la tarjeta blanca con 3:1")
    void alertInfoBorderIsVisible() {
        int[] border = colorOf(".alert-info", "border-color", token("white"));

        assertThat(contrast(border, token("white"))).isGreaterThanOrEqualTo(NON_TEXT_MINIMUM);
    }

    @Test
    @DisplayName("el enlace para saltar al contenido dibuja su foco en blanco sobre la cabecera oscura")
    void skipLinkFocusRingIsWhite() {
        assertThat(declarations(".focus\\:outline-white:focus").orElse(""))
                .contains("outline-color:#fff");
    }

    // --- helpers ---

    private static int[] token(String name) {
        int[] value = TOKENS.get(name);
        assertThat(value).as("token %s", name).isNotNull();
        return value;
    }

    /** Declarations of every rule whose selector list contains {@code selector} exactly, joined by {@code ;}. */
    private static Optional<String> declarations(String selector) {
        StringBuilder joined = new StringBuilder();
        Matcher rule = RULE.matcher(css);
        while (rule.find()) {
            for (String candidate : rule.group(1).split(",")) {
                if (candidate.trim().equals(selector)) {
                    joined.append(rule.group(2)).append(';');
                }
            }
            // The next rule may start right at this rule's closing brace: let it match from there.
            rule.region(rule.end() - 1, css.length());
        }
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined.toString());
    }

    /** A colour declared as {@code property:rgb(...)} in the rule, composited over {@code backdrop}. */
    private static int[] colorOf(String selector, String property, int[] backdrop) {
        String body = declarations(selector).orElse("");
        Matcher declaration = Pattern.compile("(?:^|;)" + property + ":([^;]+)").matcher(body);
        assertThat(declaration.find()).as("%s declares %s", selector, property).isTrue();
        Matcher color = COLOR_VALUE.matcher(declaration.group(1));
        assertThat(color.find()).as("%s %s is an rgb() colour: %s", selector, property, declaration.group(1)).isTrue();
        int[] rgb = color.group(1) != null
                ? token(color.group(1))
                : new int[] {Integer.parseInt(color.group(2)), Integer.parseInt(color.group(3)),
                    Integer.parseInt(color.group(4))};
        String opacity = color.group(5);
        return blend(rgb, opacity.startsWith("var(") ? 1.0 : Double.parseDouble(opacity), backdrop);
    }

    private static int[] hex(String rgb) {
        return new int[] {Integer.parseInt(rgb.substring(0, 2), 16), Integer.parseInt(rgb.substring(2, 4), 16),
            Integer.parseInt(rgb.substring(4, 6), 16)};
    }

    private static double alpha(String byteHex) {
        return Integer.parseInt(byteHex, 16) / 255.0;
    }

    private static int[] blend(int[] foreground, double alpha, int[] background) {
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = (int) Math.round(foreground[i] * alpha + background[i] * (1 - alpha));
        }
        return result;
    }

    private static double contrast(int[] first, int[] second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int[] rgb) {
        double[] linear = new double[3];
        for (int i = 0; i < 3; i++) {
            double channel = rgb[i] / 255.0;
            linear[i] = channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    }
}
