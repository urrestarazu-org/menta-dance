package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.PaymentProofFile;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.usecase.BankTransferInstructionsView;
import com.menta.bff.application.usecase.PayPhysicalPurchaseByTransferUseCase;
import com.menta.bff.infrastructure.web.dto.PhysicalPurchaseForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

/**
 * Web purchase of a physical class by bank transfer (#553): show where to transfer and forward the
 * proof of transfer. It only navigates and shows: the Billing API verifies the payment and decides
 * the accreditation, so a proof upload is worded as "we will verify it", never as "paid".
 *
 * <p>The instructions are kept in the server-side session (the latest transfer only) so the page
 * survives a reload and every proof failure can lead back to it. Neither the file content nor the
 * access token is ever logged.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class PhysicalBankTransferController {

    /** Session attribute holding the instructions (field name to value) of the latest transfer. */
    static final String TRANSFER_SESSION_ATTRIBUTE = "com.menta.bff.BANK_TRANSFER";

    private static final String TRANSFER_PATH = "/compras/transferencia";
    private static final String CLASSES_PATH = "/classes";
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern PAYMENT_ID = Pattern.compile(UUID_PATTERN);

    private final PayPhysicalPurchaseByTransferUseCase payByTransferUseCase;

    /** Starts the transfer payment, keeps its instructions and redirects to the instructions page. */
    @PostMapping(TRANSFER_PATH)
    public String start(
            @Valid @ModelAttribute("form") PhysicalPurchaseForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            Model model
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String back = classPath(form.getCourseId());
        if (bindingResult.hasErrors()) {
            return PurchaseFlowErrors.apply(
                    new PhysicalPurchaseApiClient.InvalidRequestException("invalid purchase form"),
                    model, response, back, "Volver a la clase");
        }
        try {
            BankTransferInstructionsView view = payByTransferUseCase.start(
                    accessToken(request), form.getQuoteId(), form.getIdempotencyKey());
            session.setAttribute(TRANSFER_SESSION_ATTRIBUTE, Map.of(
                    "paymentId", view.paymentId(), "cbu", view.cbu(), "alias", view.alias(),
                    "holder", view.holder(), "cuit", view.cuit(), "amountLabel", view.amountLabel(),
                    "reference", view.reference()));
            return "redirect:" + TRANSFER_PATH;
        } catch (PhysicalPurchaseApiClient.PurchaseFailureException
                 | BillingApiClient.NotFoundException
                 | BillingApiClient.ServiceUnavailableException e) {
            return PurchaseFlowErrors.apply(e, model, response, back, "Volver a la clase");
        }
    }

    /** Shows the instructions of the latest transfer started in this session. */
    @GetMapping(TRANSFER_PATH)
    public String instructions(HttpServletResponse response, HttpSession session, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Object transfer = session.getAttribute(TRANSFER_SESSION_ATTRIBUTE);
        if (transfer == null) {
            return "redirect:" + CLASSES_PATH;
        }
        model.addAttribute("transfer", transfer);
        return "purchase-transfer";
    }

    /** Forwards the proof of transfer; the API validates its type, size and content. */
    @PostMapping(TRANSFER_PATH + "/comprobante")
    public String uploadProof(
            @RequestParam(required = false) String paymentId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (paymentId == null || !PAYMENT_ID.matcher(paymentId).matches() || file == null || file.isEmpty()) {
            return PurchaseFlowErrors.apply(new PhysicalPurchaseApiClient.ProofRejectedException("no proof file"),
                    model, response, TRANSFER_PATH, "Volver a las instrucciones");
        }
        try {
            payByTransferUseCase.submitProof(accessToken(request), paymentId, toProof(file));
        } catch (BillingApiClient.NotFoundException e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return PurchaseFlowErrors.notice(model, "Pago no encontrado",
                    "No encontramos este pago o ya no espera un comprobante. Si ya lo enviaste, "
                            + "lo estamos verificando.", CLASSES_PATH, "Ver clases presenciales");
        } catch (PhysicalPurchaseApiClient.PurchaseFailureException
                 | BillingApiClient.ServiceUnavailableException e) {
            return PurchaseFlowErrors.apply(e, model, response, TRANSFER_PATH, "Volver a las instrucciones");
        }
        return PurchaseFlowErrors.notice(model, "Recibimos tu comprobante",
                "Recibimos tu comprobante y lo vamos a verificar. Tu lugar se confirma después de verificar "
                        + "el pago.", CLASSES_PATH, "Ver clases presenciales");
    }

    /** A file over the multipart limit is a rejected proof, not a server error. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String proofTooBig(HttpServletResponse response, Model model) {
        return PurchaseFlowErrors.apply(new PhysicalPurchaseApiClient.ProofRejectedException("proof too big"),
                model, response, TRANSFER_PATH, "Volver a las instrucciones");
    }

    private static PaymentProofFile toProof(MultipartFile file) {
        try {
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            return new PaymentProofFile(file.getOriginalFilename(), contentType, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded proof", e);
        }
    }

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    private static String classPath(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return CLASSES_PATH;
        }
        return CLASSES_PATH + "/" + UriUtils.encodePathSegment(courseId, StandardCharsets.UTF_8);
    }
}
