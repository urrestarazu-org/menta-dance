package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.ManualCheckInResult;
import com.menta.bff.application.usecase.RegisterManualCheckInUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Manual check-in, {@code POST /gestion/recepcion/sesiones/{sessionId}/asistencias} (#373), submitted by the
 * "Registrar asistencia" form of each pending student of the roster. Access is restricted to {@code RECEPTIONIST}
 * and {@code ADMIN} in {@code BffSecurityConfig}, CSRF is enforced by Spring Security, and the Physical API makes
 * the real decision (role, spot, session state) from the token: the BFF only sends the session and the student.
 *
 * <p>Every answer is Post/Redirect/Get to the roster, where the outcome is shown once from a flash attribute:
 * the confirmations in a status region, each refusal in an alert. A repeated registration is not an error: the
 * API answers with the attendance that exists, and the screen says it was already registered. The redirect is
 * never a URL from the client: it is the fixed roster path with the session id re-validated by the use case
 * and encoded as one segment, and at most the search text, encoded too. When the session id is not a UUID it
 * goes back to today's sessions. The API's answer (its body, its code) never reaches the browser, and no
 * identifier of a student travels in a URL.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementReceptionCheckInController {

    static final String REGISTERED = "Registramos la asistencia.";
    static final String ALREADY_REGISTERED =
            "La asistencia ya estaba registrada (%s, %s). No se duplicó.";
    static final String NO_SPOT =
            "El alumno no tiene un cupo confirmado en esta sesión, así que no se puede registrar su asistencia.";
    static final String OUTSIDE_WINDOW =
            "La sesión está fuera de la ventana de check-in: no se puede registrar la asistencia.";
    static final String CANCELLED = "La sesión está cancelada: no admite asistencias.";
    static final String INVALID = "Revisá los datos e intentá de nuevo.";
    static final String UNAVAILABLE = "No pudimos registrar la asistencia. Probá de nuevo en unos minutos.";

    private static final String LIST_PATH = "/gestion/recepcion";
    private static final String ROSTER_PATH = LIST_PATH + "/sesiones/{sessionId}";
    /** The search box allows no more; a longer text did not come from it. */
    private static final int MAX_TEXT_LENGTH = 100;

    private final RegisterManualCheckInUseCase registerManualCheckInUseCase;

    @PostMapping(ROSTER_PATH + "/asistencias")
    public String register(
            @PathVariable String sessionId,
            @RequestParam(required = false) String studentId,
            @RequestParam(name = "q", required = false) String text,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        ManualCheckInResult result = registerManualCheckInUseCase.execute(accessToken, sessionId, studentId);
        switch (result.outcome()) {
            case REGISTERED -> redirectAttributes.addFlashAttribute("checkInSuccess", REGISTERED);
            case ALREADY_REGISTERED -> redirectAttributes.addFlashAttribute("checkInInfo",
                    ALREADY_REGISTERED.formatted(result.kindLabel(), result.recordedAtLabel()));
            case NO_SPOT -> redirectAttributes.addFlashAttribute("checkInError", NO_SPOT);
            case OUTSIDE_WINDOW -> redirectAttributes.addFlashAttribute("checkInError", OUTSIDE_WINDOW);
            case SESSION_CANCELLED -> redirectAttributes.addFlashAttribute("checkInError", CANCELLED);
            case INVALID -> redirectAttributes.addFlashAttribute("checkInError", INVALID);
            default -> redirectAttributes.addFlashAttribute("checkInError", UNAVAILABLE);
        }
        if (result.outcome() == ManualCheckInResult.Outcome.INVALID) {
            return "redirect:" + LIST_PATH;
        }
        return "redirect:" + rosterUrl(sessionId, text);
    }

    /**
     * The roster's address with the search the staff member was in. The values are expanded as URI variables,
     * which are encoded strictly: whatever the client sent can neither add a parameter or a fragment nor change
     * the path.
     */
    private static String rosterUrl(String sessionId, String text) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(ROSTER_PATH);
        Map<String, Object> variables = new HashMap<>();
        variables.put("sessionId", sessionId.strip().toLowerCase(Locale.ROOT));
        String cleanText = text == null ? "" : text.strip();
        if (!cleanText.isEmpty() && cleanText.length() <= MAX_TEXT_LENGTH) {
            builder.queryParam("q", "{q}");
            variables.put("q", cleanText);
        }
        return builder.encode().build(variables).toString();
    }
}
