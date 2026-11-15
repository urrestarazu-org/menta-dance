package com.menta.billing.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.application.dto.OwnPaymentEntry;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentStatus;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between {@link OwnPaymentResponse} and {@code api/openapi/billing-v1.yaml} for
 * {@code GET /api/v1/billing/payments/me} (#767). The yaml is hand-written, so this is what fails
 * when the record, the domain enums or the persisted status vocabulary and the contract diverge.
 */
class OwnPaymentsContractTest {

    private static final String PATH = "/api/v1/billing/payments/me";
    private static final List<String> FORBIDDEN = List.of(
        "userId", "providerPaymentId", "externalReference", "expectedExternalReference", "merchantAccountId",
        "expectedMerchantAccountId", "statusReason", "reason", "proof", "proofUrl", "storageKey"
    );

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

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    /**
     * Every concrete variant of the sealed {@link PaymentStatus}, in {@code PaymentJpaMapper}'s
     * {@code status_type} spelling ({@code AwaitingProvider} is {@code AWAITING_PROVIDER}): a new
     * status in the domain fails this test until the contract lists it.
     */
    private static List<String> domainStatuses() {
        return leaves(PaymentStatus.class)
            .map(type -> type.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT))
            .toList();
    }

    private static Stream<Class<?>> leaves(Class<?> type) {
        return type.isSealed() ? Arrays.stream(type.getPermittedSubclasses()).flatMap(OwnPaymentsContractTest::leaves)
            : Stream.of(type);
    }

    @Test
    void the_route_documents_200_400_and_401() {
        Map<String, Object> get = map(map(map(spec, "paths"), PATH), "get");

        assertThat(map(get, "responses")).containsKeys("200", "400", "401");
    }

    @Test
    void the_schemas_match_the_response_records() {
        assertThat(map(schema("OwnPaymentResponse"), "properties").keySet())
            .containsExactlyInAnyOrderElementsOf(componentNames(OwnPaymentResponse.class));
        assertThat(map(schema("OwnPaymentItem"), "properties").keySet())
            .containsExactlyInAnyOrderElementsOf(componentNames(OwnPaymentResponse.Item.class));
    }

    @Test
    void the_enums_match_the_domain() {
        assertThat(property("OwnPaymentResponse", "status").get("enum"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .containsExactlyInAnyOrderElementsOf(domainStatuses())
            .hasSize(7);
        assertThat(property("OwnPaymentResponse", "paymentMethod").get("enum"))
            .isEqualTo(names(PaymentMethod.class));
        assertThat(property("OwnPaymentItem", "type").get("enum"))
            .isEqualTo(names(OwnPaymentEntry.ItemType.class));
    }

    @Test
    void the_schema_never_exposes_internal_payment_data() {
        assertThat(map(schema("OwnPaymentResponse"), "properties").keySet()).doesNotContainAnyElementsOf(FORBIDDEN);
    }
}
