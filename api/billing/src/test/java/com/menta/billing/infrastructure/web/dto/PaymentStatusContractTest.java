package com.menta.billing.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.FulfillmentStatus;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between {@link PaymentStatusResponse} and {@code api/openapi/billing-v1.yaml} for
 * {@code GET /api/v1/billing/payments/{paymentId}} (#559). The yaml is hand-written, so this is the
 * only thing that fails when the record, the {@link FulfillmentStatus} enum and the contract
 * diverge.
 */
class PaymentStatusContractTest {

    private static final String PATH = "/api/v1/billing/payments/{paymentId}";
    private static final List<String> FORBIDDEN =
        List.of("reason", "resolution", "resolutionType", "resolutionNote", "note");

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

    private static Map<String, Object> schema() {
        return map(map(map(spec, "components"), "schemas"), "PaymentStatusResponse");
    }

    @Test
    void the_student_payment_read_documents_200_and_404() {
        Map<String, Object> get = map(map(map(spec, "paths"), PATH), "get");

        assertThat(map(get, "responses")).containsKeys("200", "404");
    }

    @Test
    void the_schema_properties_match_the_response_record_components() {
        List<String> recordComponents = Arrays.stream(PaymentStatusResponse.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertThat(map(schema(), "properties").keySet()).containsExactlyInAnyOrderElementsOf(recordComponents);
    }

    @Test
    @SuppressWarnings("unchecked")
    void the_fulfillment_enum_matches_the_domain_enum_plus_null() {
        Map<String, Object> fulfillment = map(map(schema(), "properties"), "fulfillment");
        List<Object> values = new ArrayList<>((List<Object>) fulfillment.get("enum"));

        assertThat(values).containsNull();
        values.remove(null);
        assertThat(values).containsExactlyInAnyOrderElementsOf(
            Arrays.stream(FulfillmentStatus.values()).map(Enum::name).toList());
        assertThat(fulfillment.get("type")).isEqualTo(List.of("string", "null"));
    }

    @Test
    void the_schema_never_exposes_internal_purchase_detail() {
        assertThat(map(schema(), "properties").keySet()).doesNotContainAnyElementsOf(FORBIDDEN);
    }
}
