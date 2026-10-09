package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.GetCatalogViewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Home controller — the anonymous-reachable landing page showing the public
 * course catalog (#358).
 *
 * <p>The optional {@code modality} query parameter ({@code virtual} or
 * {@code physical}) is passed through raw; the use case decides that anything
 * else means "all". An upstream outage does not reach this controller: the use
 * case degrades to a flagged view, so the page always renders.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final GetCatalogViewUseCase getCatalogViewUseCase;

    @GetMapping("/")
    public String home(@RequestParam(name = "modality", required = false) String modality, Model model) {
        model.addAttribute("catalog", getCatalogViewUseCase.execute(modality));
        return "index";
    }

    @GetMapping("/health")
    public String health() {
        return "health";
    }
}
