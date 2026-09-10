package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.GetPlansViewUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Plans listing controller — anonymous-reachable (spec: {@code
 * bff-plans-view}, "Anonymous-reachable plans route").
 * <p>
 * Delegates straight to {@link GetPlansViewUseCase} and renders every plan
 * it returns. No try/catch, mirroring {@link CourseDetailController}:
 * uncaught, {@code @ResponseStatus}-annotated upstream failures ({@link
 * com.menta.bff.application.port.out.BillingApiClient.NotFoundException} /
 * {@link com.menta.bff.application.port.out.BillingApiClient.ServiceUnavailableException})
 * resolve through Spring Boot's default {@code /error} → {@code error.html}
 * path, so no upstream problem-detail body ever reaches the browser (design
 * D6).
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
public class PlansController {

    private final GetPlansViewUseCase getPlansViewUseCase;

    public PlansController(GetPlansViewUseCase getPlansViewUseCase) {
        this.getPlansViewUseCase = getPlansViewUseCase;
    }

    @GetMapping("/plans")
    public String plans(Model model) {
        model.addAttribute("plans", getPlansViewUseCase.execute());
        return "plans";
    }
}
