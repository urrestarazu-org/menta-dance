package com.menta.billing.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.application.dto.BankTransferInstructions;
import com.menta.billing.application.dto.OverlapNotice;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.infrastructure.web.dto.CreatePhysicalCourseQuoteRequest;
import com.menta.billing.infrastructure.web.dto.CreatePhysicalPurchaseRequest;
import com.menta.billing.infrastructure.web.dto.CreateSubscriptionRequest;
import com.menta.billing.infrastructure.web.dto.PaymentDetailResponse;
import com.menta.billing.infrastructure.web.dto.PhysicalCoursePricingResponse;
import com.menta.billing.infrastructure.web.dto.PhysicalCourseQuoteResponse;
import com.menta.billing.infrastructure.web.dto.PhysicalPurchaseCheckoutResponse;
import com.menta.billing.infrastructure.web.dto.RejectPaymentRequest;
import com.menta.billing.infrastructure.web.dto.SubscriptionCheckoutResponse;
import com.menta.billing.infrastructure.web.dto.UpdatePhysicalCoursePricingRequest;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between the billing controllers/DTOs and {@code api/openapi/billing-v1.yaml} (#774).
 * The yaml is hand-written, so this is what fails when a route, a record or a domain enum changes
 * without the contract: the route set is read from the real controllers, and the schemas from the
 * real records (Jackson writes {@code null} instead of omitting, so a response requires every
 * component).
 */
class BillingOpenApiContractTest {

    private static final String PAYMENTS = "/api/v1/billing/payments/{paymentId}";
    private static final String ADMIN_PAYMENTS = "/api/v1/admin/billing/payments/{paymentId}";
    private static final String PRICING = "/api/v1/billing/physical/courses/{courseId}/pricing";
    private static final Set<String> VALIDATION_REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");

    private static Map<String, Object> spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        // Gradle runs module tests with the module directory as the working directory.
        try (InputStream in = Files.newInputStream(Path.of("..", "openapi", "billing-v1.yaml"))) {
            spec = new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object parent, String key) {
        Object value = ((Map<String, Object>) parent).get(key);
        assertThat(value).as("yaml key '%s'", key).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> schema(String name) {
        return map(map(map(spec, "components"), "schemas"), name);
    }

    private static Map<String, Object> property(String schema, String name) {
        return map(map(schema(schema), "properties"), name);
    }

    private static List<String> required(String schema) {
        Object required = schema(schema).get("required");
        return required == null ? List.of() : stringList(required);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return (List<String>) value;
    }

    private static Map<String, Object> operation(String route) {
        String[] parts = route.split(" ", 2);
        return map(map(map(spec, "paths"), parts[1]), parts[0].toLowerCase(Locale.ROOT));
    }

    private static List<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<String> mandatoryComponents(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
            .filter(component -> Arrays.stream(component.getAccessor().getAnnotations())
                .anyMatch(annotation -> VALIDATION_REQUIRED.contains(annotation.annotationType().getSimpleName())))
            .map(RecordComponent::getName)
            .toList();
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    private static String normalized(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

    private static Set<String> codeRoutes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> routes = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.menta.billing")) {
            Class<?> controller = load(candidate.getBeanClassName());
            if (controller.isAnnotationPresent(Profile.class)) {
                continue; // e2e-only support endpoints are not part of the contract
            }
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String prefix = base == null || base.path().length == 0 ? "" : base.path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping != null) {
                    String suffix = mapping.path().length == 0 ? "" : mapping.path()[0];
                    routes.add(mapping.method()[0].name() + " " + normalized(prefix + suffix));
                }
            }
        }
        return routes;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> documentedRoutes() {
        Set<String> routes = new TreeSet<>();
        map(spec, "paths").forEach((path, item) -> {
            for (Object verb : map(map(spec, "paths"), path).keySet()) {
                routes.add(verb.toString().toUpperCase(Locale.ROOT) + " " + normalized(path));
            }
        });
        return routes;
    }

    private static void assertResponses(String route, String... statuses) {
        assertThat(map(operation(route), "responses")).as(route).containsKeys(statuses);
    }

    @Test
    void every_production_route_is_documented_and_nothing_else_is() {
        assertThat(documentedRoutes()).containsExactlyInAnyOrderElementsOf(codeRoutes());
    }

    @Test
    void the_payment_routes_document_their_status_codes() {
        assertResponses("POST " + ADMIN_PAYMENTS + "/approve", "200", "400", "401", "403", "404", "409");
        assertResponses("POST " + ADMIN_PAYMENTS + "/reject", "200", "400", "401", "403", "404", "409");
        assertResponses("POST " + PAYMENTS + "/proof", "200", "400", "401", "404", "429", "503");
        assertResponses("GET " + ADMIN_PAYMENTS, "200", "400", "401", "403", "404");
        assertResponses("POST " + ADMIN_PAYMENTS + "/corrections", "200", "400", "401", "403", "404", "409");
        assertResponses("GET " + PAYMENTS, "200", "400", "401", "404");
    }

    @Test
    void the_physical_pricing_and_quote_routes_document_their_status_codes() {
        assertResponses("GET " + PRICING, "200", "404");
        assertResponses("PUT " + PRICING, "200", "400", "401", "403", "404", "422");
        assertResponses("POST /api/v1/billing/physical/quotes", "201", "400", "401", "404", "409", "422");
    }

    @Test
    void the_subscription_checkout_documents_the_bank_transfer_errors() {
        assertResponses("POST /api/v1/billing/subscriptions", "201", "400", "401", "409", "422", "429", "503");
    }

    @Test
    void the_public_pricing_read_has_no_security_and_the_staff_update_does() {
        assertThat(operation("GET " + PRICING).get("security")).isEqualTo(List.of());
        assertThat(operation("PUT " + PRICING).get("security")).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
    }

    @Test
    void the_proof_upload_is_a_multipart_with_a_required_binary_file() {
        Map<String, Object> multipart = map(map(map(operation("POST " + PAYMENTS + "/proof"), "requestBody"),
            "content"), "multipart/form-data");
        Map<String, Object> body = map(multipart, "schema");

        assertThat(body.get("required")).isEqualTo(List.of("file"));
        assertThat(map(map(body, "properties"), "file")).containsEntry("format", "binary");
    }

    @Test
    void the_proof_read_serves_the_media_types_the_upload_accepts() {
        Map<String, Object> ok = map(map(operation("GET " + PAYMENTS + "/proof"), "responses"), "200");

        assertThat(map(ok, "content").keySet())
            .containsExactlyInAnyOrder("image/png", "image/jpeg", "application/pdf");
    }

    @Test
    void the_response_schemas_match_the_records_and_require_every_component() {
        Map<String, Class<? extends Record>> responses = Map.of(
            "SubscriptionCheckoutResponse", SubscriptionCheckoutResponse.class,
            "PhysicalPurchaseCheckoutResponse", PhysicalPurchaseCheckoutResponse.class,
            "PaymentDetailResponse", PaymentDetailResponse.class,
            "PhysicalCoursePricingResponse", PhysicalCoursePricingResponse.class,
            "PhysicalCourseQuoteResponse", PhysicalCourseQuoteResponse.class,
            "BankTransferInstructions", BankTransferInstructions.class,
            "OverlapNotice", OverlapNotice.class
        );

        List<String> drift = new ArrayList<>();
        responses.forEach((name, type) -> {
            if (!new TreeSet<>(map(schema(name), "properties").keySet()).equals(new TreeSet<>(components(type)))) {
                drift.add(name + " properties");
            }
            if (!new TreeSet<>(required(name)).equals(new TreeSet<>(components(type)))) {
                drift.add(name + " required");
            }
        });
        assertThat(drift).isEmpty();
    }

    @Test
    void the_request_schemas_match_the_records_and_require_what_validation_requires() {
        Map<String, Class<? extends Record>> requests = Map.of(
            "CreateSubscriptionCheckoutRequest", CreateSubscriptionRequest.class,
            "CreatePhysicalPurchaseRequest", CreatePhysicalPurchaseRequest.class,
            "RejectPaymentRequest", RejectPaymentRequest.class,
            "UpdatePhysicalCoursePricingRequest", UpdatePhysicalCoursePricingRequest.class,
            "CreatePhysicalCourseQuoteRequest", CreatePhysicalCourseQuoteRequest.class
        );

        List<String> drift = new ArrayList<>();
        requests.forEach((name, type) -> {
            if (!new TreeSet<>(map(schema(name), "properties").keySet()).equals(new TreeSet<>(components(type)))) {
                drift.add(name + " properties");
            }
            if (!new TreeSet<>(required(name)).equals(new TreeSet<>(mandatoryComponents(type)))) {
                drift.add(name + " required");
            }
        });
        assertThat(drift).isEmpty();
    }

    @Test
    void the_enums_match_the_domain() {
        assertThat(property("CreateSubscriptionCheckoutRequest", "paymentMethod").get("enum"))
            .isEqualTo(names(PaymentMethod.class));
        assertThat(property("CreatePhysicalCourseQuoteRequest", "purchaseType").get("enum"))
            .isEqualTo(names(PurchaseType.class));
        assertThat(property("PhysicalCourseQuoteResponse", "purchaseType").get("enum"))
            .isEqualTo(names(PurchaseType.class));
        assertThat(property("PhysicalCourseQuoteResponse", "availability").get("enum"))
            .isEqualTo(names(QuoteAvailability.class));
        // A replay by idempotencyKey returns the subscription as it is now, not as it was created.
        assertThat(property("SubscriptionCheckoutResponse", "status").get("enum"))
            .isEqualTo(names(SubscriptionStatus.class));
    }
}
