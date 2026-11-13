package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.GetSessionRosterUseCase;
import com.menta.bff.application.usecase.ListTodaySessionsUseCase;
import com.menta.bff.application.usecase.ReceptionSessionsView;
import com.menta.bff.application.usecase.SessionRosterView;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Reception screens (#373): today's sessions, {@code GET /gestion/recepcion}, and the roster of one of them,
 * {@code GET /gestion/recepcion/sesiones/{sessionId}}, with a search by name in {@code ?q=}. Access is restricted
 * to {@code RECEPTIONIST} and {@code ADMIN} in {@code BffSecurityConfig}; the Physical API makes the real
 * decision with the session's access token.
 *
 * <p>The pages hold the students' names, so they are never cached. An outage of the Physical API degrades to the
 * page's own notice with status 503 and an unknown session to a notice with status 404; upstream details never
 * reach the browser. The search text is only echoed, always escaped by the template.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementReceptionController {

    private final ListTodaySessionsUseCase listTodaySessionsUseCase;
    private final GetSessionRosterUseCase getSessionRosterUseCase;

    @GetMapping("/gestion/recepcion")
    public String sessions(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        ReceptionSessionsView agenda = listTodaySessionsUseCase.execute(accessToken);
        if (agenda.unavailable()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("agenda", agenda);
        return "gestion/recepcion/list";
    }

    @GetMapping("/gestion/recepcion/sesiones/{sessionId}")
    public String roster(
            @PathVariable String sessionId,
            @RequestParam(name = "q", required = false) String text,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        SessionRosterView roster = getSessionRosterUseCase.execute(accessToken, sessionId, text);
        if (roster.outcome() == SessionRosterView.Outcome.NOT_FOUND) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } else if (roster.outcome() == SessionRosterView.Outcome.UNAVAILABLE) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("roster", roster);
        model.addAttribute("sessionId", sessionId);
        return "gestion/recepcion/session";
    }
}
