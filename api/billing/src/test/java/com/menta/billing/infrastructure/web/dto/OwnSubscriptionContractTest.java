package com.menta.billing.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between {@link CurrentSubscriptionResponse} / {@link SubscriptionHistoryItemResponse}
 * and {@code api/openapi/billing-v1.yaml} for {@code GET /api/v1/billing/subscriptions/me} and
 * {@code GET .../me/history} (#766). The yaml is hand-written, so this is what fails when the
 * records or the domain enums and the contract diverge.
 */
class OwnSubscriptionContractTest {

    private static final String CURRENT_PATH = "/api/v1/billing/subscriptions/me";
    private static final String HISTORY_PATH = "/api/v1/billing/subscriptions/me/history";

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

    @Test
    void both_routes_are_documented_with_their_responses() {
        Map<String, Object> paths = map(spec, "paths");

        assertThat(map(map(map(paths, CURRENT_PATH), "get"), "responses")).containsKeys("200", "401", "404");
        assertThat(map(map(map(paths, HISTORY_PATH), "get"), "responses")).containsKeys("200", "401");
    }

    @Test
    void the_schemas_match_the_response_records_and_require_every_field() {
        assertThat(map(schema("CurrentSubscriptionResponse"), "properties").keySet())
            .containsExactlyInAnyOrderElementsOf(componentNames(CurrentSubscriptionResponse.class));
        assertThat(schema("CurrentSubscriptionResponse").get("required"))
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .containsExactlyInAnyOrderElementsOf(componentNames(CurrentSubscriptionResponse.class));
        assertThat(map(schema("SubscriptionHistoryItemResponse"), "properties").keySet())
            .containsExactlyInAnyOrderElementsOf(componentNames(SubscriptionHistoryItemResponse.class));
        assertThat(schema("SubscriptionHistoryItemResponse").get("required"))
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .containsExactlyInAnyOrderElementsOf(componentNames(SubscriptionHistoryItemResponse.class));
    }

    @Test
    void the_type_enum_matches_the_domain_in_both_schemas() {
        assertThat(property("CurrentSubscriptionResponse", "type").get("enum"))
            .isEqualTo(names(SubscriptionType.class));
        assertThat(property("SubscriptionHistoryItemResponse", "type").get("enum"))
            .isEqualTo(names(SubscriptionType.class));
    }

    @Test
    void history_lists_every_domain_status() {
        assertThat(property("SubscriptionHistoryItemResponse", "status").get("enum"))
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .containsExactlyInAnyOrderElementsOf(names(SubscriptionStatus.class));
    }
}
