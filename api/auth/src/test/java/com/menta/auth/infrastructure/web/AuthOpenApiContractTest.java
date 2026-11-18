package com.menta.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.web.dto.ActivateAccountRequest;
import com.menta.auth.infrastructure.web.dto.ActivationTokenValidationResponse;
import com.menta.auth.infrastructure.web.dto.ChangeUserRoleRequest;
import com.menta.auth.infrastructure.web.dto.CreateUserRequest;
import com.menta.auth.infrastructure.web.dto.CurrentUserResponse;
import com.menta.auth.infrastructure.web.dto.ForgotPasswordRequest;
import com.menta.auth.infrastructure.web.dto.LoginRequest;
import com.menta.auth.infrastructure.web.dto.RegisterUserRequest;
import com.menta.auth.infrastructure.web.dto.ResendActivationRequest;
import com.menta.auth.infrastructure.web.dto.ResetPasswordRequest;
import com.menta.auth.infrastructure.web.dto.TokenResponse;
import com.menta.auth.infrastructure.web.dto.UserPageResponse;
import com.menta.auth.infrastructure.web.dto.UserSummaryResponse;
import com.menta.shared.domain.exceptions.BusinessException;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards drift between the auth controllers/DTOs and {@code api/openapi/auth-v1.yaml} (#774). The yaml is
 * hand-written, so this is what fails when a route, a record, an enum or an error code changes without the
 * contract. Routes come from the real controllers, request schemas from the real records and their validation,
 * response schemas from what Jackson really writes (it writes {@code null} instead of omitting, so a response
 * requires every property and only the ones that can be {@code null} may declare it), and the set of error codes
 * from the real exceptions.
 */
class AuthOpenApiContractTest {

    private static final String LOGIN = "POST /api/v1/auth/login";
    private static final String REFRESH = "POST /api/v1/auth/refresh";
    private static final String LOGOUT = "POST /api/v1/auth/logout";
    private static final String REGISTER = "POST /api/v1/auth/register";
    private static final String REGISTER_ALIAS = "POST /api/v1/users/register";
    private static final String ME = "GET /api/v1/users/me";
    private static final String VALIDATE_ACTIVATION = "GET /api/v1/auth/activate/{token}";
    private static final String ACTIVATE = "POST /api/v1/auth/activate";
    private static final String RESEND = "POST /api/v1/auth/resend-activation";
    private static final String FORGOT = "POST /api/v1/auth/forgot-password";
    private static final String RESET = "POST /api/v1/auth/reset-password";
    private static final String LIST_USERS = "GET /api/v1/admin/users";
    private static final String CREATE_USER = "POST /api/v1/admin/users";
    private static final String CHANGE_ROLE = "PATCH /api/v1/admin/users/{userId}/role";
    private static final Set<String> VALIDATION_REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");

    private static Map<String, Object> spec;

    // Same defaults as Spring Boot's mapper (unknown properties ignored, ISO dates and durations).
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        .build();

    @BeforeAll
    static void loadSpec() throws IOException {
        // Gradle runs module tests with the module directory as the working directory.
        try (InputStream in = Files.newInputStream(Path.of("..", "openapi", "auth-v1.yaml"))) {
            spec = new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object parent, String key) {
        Object value = ((Map<String, Object>) parent).get(key);
        assertThat(value).as("yaml key '%s'", key).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> schema(String name) {
        return map(map(map(spec, "components"), "schemas"), name);
    }

    private static Map<String, Object> operation(String route) {
        String[] parts = route.split(" ", 2);
        return map(map(map(spec, "paths"), parts[1]), parts[0].toLowerCase(Locale.ROOT));
    }

    private static String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    /** Follows {@code $ref} and takes the non-null branch of a {@code oneOf}. */
    private static Map<String, Object> resolve(Map<String, Object> node) {
        if (node.get("$ref") instanceof String ref) {
            return resolve(schema(refName(ref)));
        }
        if (node.get("oneOf") != null) {
            return resolve(list(node.get("oneOf")).stream()
                .map(AuthOpenApiContractTest::asMap)
                .filter(option -> !"null".equals(option.get("type")))
                .findFirst().orElseThrow());
        }
        return node;
    }

    /** A schema addressed as {@code Name/property/[]/property}, where {@code []} steps into array items. */
    private static Map<String, Object> at(String path) {
        String[] steps = path.split("/");
        Map<String, Object> node = schema(steps[0]);
        for (int i = 1; i < steps.length; i++) {
            Map<String, Object> resolved = resolve(node);
            node = "[]".equals(steps[i]) ? map(resolved, "items") : map(map(resolved, "properties"), steps[i]);
        }
        return resolve(node);
    }

    private static Set<String> propertyNames(Map<String, Object> node) {
        return new TreeSet<>(map(node, "properties").keySet());
    }

    private static Set<String> nullableNames(Map<String, Object> node) {
        Set<String> names = new TreeSet<>();
        map(node, "properties").forEach((name, property) -> {
            Object type = asMap(property).get("type");
            if (type instanceof List<?> types && types.contains("null")) {
                names.add(name);
            }
        });
        return names;
    }

    private static Set<String> requiredNames(Map<String, Object> node) {
        Set<String> names = new TreeSet<>();
        if (node.get("required") != null) {
            list(node.get("required")).forEach(name -> names.add((String) name));
        }
        return names;
    }

    private static Set<String> components(Class<? extends Record> type) {
        return new TreeSet<>(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList());
    }

    /** What a request must carry: a component validated as present. */
    private static Set<String> mandatoryComponents(Class<? extends Record> type) {
        return new TreeSet<>(Arrays.stream(type.getRecordComponents())
            .filter(component -> Arrays.stream(component.getAccessor().getAnnotations())
                .anyMatch(annotation -> VALIDATION_REQUIRED.contains(annotation.annotationType().getSimpleName())))
            .map(RecordComponent::getName)
            .toList());
    }

    private static Set<String> csv(String values) {
        return values.isEmpty() ? new TreeSet<>() : new TreeSet<>(Arrays.asList(values.split(",")));
    }

    private static String normalized(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

    private static Set<String> codeRoutes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> routes = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.menta.auth")) {
            Class<?> controller = load(candidate.getBeanClassName());
            // UserController answers under two prefixes, so every class-level path counts.
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[] {""} : base.path();
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] suffixes = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String suffix : suffixes) {
                        routes.add(mapping.method()[0].name() + " " + normalized(prefix + suffix));
                    }
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
        map(spec, "paths").forEach((path, item) -> asMap(item).keySet().forEach(verb ->
            routes.add(verb.toUpperCase(Locale.ROOT) + " " + normalized(path))));
        return routes;
    }

    /** A response, with {@code $ref} to {@code components/responses} followed. */
    private static Map<String, Object> response(Object node) {
        Map<String, Object> response = asMap(node);
        return response.get("$ref") instanceof String ref
            ? map(map(map(spec, "components"), "responses"), refName(ref))
            : response;
    }

    /** The {@code code} of every problem example an operation documents, whatever its status. */
    private static Set<String> documentedCodes(Map<String, Object> operation) {
        Set<String> codes = new TreeSet<>();
        map(operation, "responses").values().forEach(node -> {
            Map<String, Object> content = asMap(response(node).get("content"));
            if (content == null || content.get("application/problem+json") == null) {
                return;
            }
            Map<String, Object> problem = map(content, "application/problem+json");
            if (problem.get("example") != null) {
                codes.add((String) asMap(problem.get("example")).get("code"));
            }
            if (problem.get("examples") != null) {
                asMap(problem.get("examples")).values().forEach(example ->
                    codes.add((String) asMap(asMap(example).get("value")).get("code")));
            }
        });
        return codes;
    }

    private void expect(Map<String, String[]> expected, String route, String statuses, String codes) {
        expected.put(route, new String[] {statuses, codes});
    }

    @Test
    void every_production_route_is_documented_and_nothing_else_is() {
        assertThat(documentedRoutes()).containsExactlyInAnyOrderElementsOf(codeRoutes());
    }

    @Test
    void every_route_documents_exactly_the_statuses_and_error_codes_its_advice_and_security_rules_can_answer() {
        Map<String, String[]> expected = new LinkedHashMap<>();
        // AuthController's own handlers: INVALID_AUTH_REQUEST for a bad body or header.
        expect(expected, LOGIN, "200,400,401,403,423,429,503",
            "INVALID_AUTH_REQUEST,INVALID_CREDENTIALS,ACCOUNT_NOT_ACTIVATED,USER_LOCKED,LOGIN_RATE_LIMITED,"
                + "AUTH_DEGRADED");
        expect(expected, REFRESH, "200,400,401,503", "INVALID_AUTH_REQUEST,REFRESH_TOKEN_COMPROMISED,AUTH_DEGRADED");
        // Logout is authenticated: the 401 is the security filter's or the refresh token's.
        expect(expected, LOGOUT, "204,400,401,503",
            "INVALID_AUTH_REQUEST,AUTHENTICATION_REQUIRED,REFRESH_TOKEN_COMPROMISED,AUTH_DEGRADED");
        // Registration: ActivationExceptionHandler. A role other than STUDENT is a 202, not an error.
        expect(expected, REGISTER, "202,400,429,503", "INVALID_REQUEST,ACTIVATION_RATE_LIMITED,AUTH_DEGRADED");
        expect(expected, REGISTER_ALIAS, "202,400,429,503", "INVALID_REQUEST,ACTIVATION_RATE_LIMITED,AUTH_DEGRADED");
        expect(expected, ME, "200,401", "AUTHENTICATION_REQUIRED");
        // The 503 of both activation routes is documented although no use case behind them raises it today.
        expect(expected, VALIDATE_ACTIVATION, "200,204,400,503",
            "ACTIVATION_TOKEN_INVALID,INVALID_ACTIVATION_REQUEST,AUTH_DEGRADED");
        expect(expected, ACTIVATE, "204,400,503",
            "ACTIVATION_TOKEN_INVALID,PASSWORD_REQUIRED,WEAK_PASSWORD,INVALID_REQUEST,INVALID_ACTIVATION_REQUEST,"
                + "AUTH_DEGRADED");
        expect(expected, RESEND, "202,400,429,503",
            "INVALID_REQUEST,INVALID_ACTIVATION_REQUEST,ACTIVATION_RATE_LIMITED,AUTH_DEGRADED");
        // Password reset has no AuthDegraded handler (a Redis outage ends in a 500), so no 503 is documented.
        expect(expected, FORGOT, "202,400,429", "INVALID_REQUEST,PASSWORD_RESET_RATE_LIMITED");
        expect(expected, RESET, "200,400,404,410,429",
            "INVALID_REQUEST,WEAK_PASSWORD,SAME_PASSWORD,PASSWORD_RESET_TOKEN_NOT_FOUND,PASSWORD_RESET_TOKEN_EXPIRED,"
                + "PASSWORD_RESET_TOKEN_ALREADY_USED,PASSWORD_RESET_RATE_LIMITED");
        // /api/v1/admin/** is ADMIN only: 401 and 403 come from SecurityConfig.
        expect(expected, LIST_USERS, "200,400,401,403", "INVALID_REQUEST,AUTHENTICATION_REQUIRED,ACCESS_DENIED");
        expect(expected, CREATE_USER, "201,400,401,403,409",
            "INVALID_REQUEST,AUTHENTICATION_REQUIRED,ACCESS_DENIED,EMAIL_ALREADY_REGISTERED");
        expect(expected, CHANGE_ROLE, "200,400,401,403,404,409",
            "INVALID_REQUEST,AUTHENTICATION_REQUIRED,ACCESS_DENIED,USER_NOT_FOUND,LAST_ADMIN_CANNOT_BE_DEMOTED");

        Map<String, List<Set<String>>> documented = new LinkedHashMap<>();
        Map<String, List<Set<String>>> wanted = new LinkedHashMap<>();
        expected.forEach((route, doc) -> {
            Map<String, Object> operation = operation(route);
            documented.put(route,
                List.of(new TreeSet<>(map(operation, "responses").keySet()), documentedCodes(operation)));
            wanted.put(route, List.of(csv(doc[0]), csv(doc[1])));
        });
        assertThat(documented).isEqualTo(wanted);
        assertThat(expected.keySet().stream().map(route -> {
            String[] parts = route.split(" ", 2);
            return parts[0] + " " + normalized(parts[1]);
        }).toList()).containsExactlyInAnyOrderElementsOf(codeRoutes());
    }

    @Test
    void every_error_response_carries_a_problem_json_body() {
        List<String> bare = new ArrayList<>();
        map(spec, "paths").forEach((path, item) -> asMap(item).forEach((verb, operation) ->
            map(operation, "responses").forEach((status, response) -> {
                Map<String, Object> resolved = response(response);
                boolean problem = resolved.get("content") != null
                    && map(resolved, "content").containsKey("application/problem+json");
                if (Integer.parseInt(status.toString()) >= 400 && !problem) {
                    bare.add(verb + " " + path + " " + status);
                }
            })));
        assertThat(bare).isEmpty();
    }

    @Test
    void the_error_code_enum_is_the_set_of_codes_the_code_can_emit() throws ReflectiveOperationException {
        // Domain exceptions carry their code in ERROR_CODE; the rest are written as literals by the handlers
        // and the security entry point and denied-access handler, so they are listed here.
        Set<String> emitted = new TreeSet<>(Set.of(
            "INVALID_AUTH_REQUEST", "INVALID_REQUEST", "INVALID_ACTIVATION_REQUEST",
            "AUTHENTICATION_REQUIRED", "ACCESS_DENIED"));
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(BusinessException.class));
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.menta.auth.domain.exception")) {
            for (Field field : load(candidate.getBeanClassName()).getDeclaredFields()) {
                if (field.getName().equals("ERROR_CODE")) {
                    field.setAccessible(true);
                    emitted.add((String) field.get(null));
                }
            }
        }
        List<Object> documented = list(schema("ErrorCode").get("enum"));
        assertThat(documented).containsExactlyInAnyOrderElementsOf(emitted);
    }

    @Test
    void only_the_public_operations_drop_the_bearer_requirement() {
        assertThat(list(spec.get("security"))).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        Set<String> publicRoutes = new TreeSet<>();
        map(spec, "paths").forEach((path, item) -> asMap(item).forEach((verb, operation) -> {
            if (asMap(operation).get("security") != null) {
                assertThat(asMap(operation).get("security")).as("%s %s", verb, path).isEqualTo(List.of());
                publicRoutes.add(verb.toUpperCase(Locale.ROOT) + " " + path);
            }
        }));
        // SecurityConfig: permitAll for these, authenticated (logout, me) or ADMIN (admin/users) for the rest.
        assertThat(publicRoutes).containsExactlyInAnyOrder(
            LOGIN, REFRESH, REGISTER, REGISTER_ALIAS, VALIDATE_ACTIVATION, ACTIVATE, RESEND, FORGOT, RESET);
    }

    /** Serializes a sample as the API does and compares its keys, nulls and values with the response schema. */
    private JsonNode wire(Object sample, String schemaPath, String... nullable) throws IOException {
        JsonNode json = mapper.valueToTree(sample);
        Map<String, Object> node = at(schemaPath);
        Set<String> keys = new TreeSet<>();
        json.fieldNames().forEachRemaining(keys::add);
        Set<String> nulls = new TreeSet<>();
        json.fields().forEachRemaining(field -> {
            if (field.getValue().isNull()) {
                nulls.add(field.getKey());
            }
        });
        assertThat(propertyNames(node)).as("%s properties", schemaPath).isEqualTo(keys);
        assertThat(requiredNames(node)).as("%s required", schemaPath).isEqualTo(keys);
        assertThat(nullableNames(node)).as("%s nullable", schemaPath).isEqualTo(new TreeSet<>(Arrays.asList(nullable)));
        assertThat(nulls).as("%s nulls written", schemaPath).isEqualTo(new TreeSet<>(Arrays.asList(nullable)));
        return json;
    }

    @Test
    void the_user_responses_match_what_jackson_writes_including_the_null_names() throws IOException {
        UserSummaryResponse summary = new UserSummaryResponse(
            UUID.randomUUID().toString(), "a@example.com", null, null, Role.ADMIN, UserStatus.ACTIVE, false,
            LocalDateTime.of(2026, 11, 10, 9, 30));
        JsonNode json = wire(summary, "UserSummary", "firstName", "lastName");
        // A whole minute still carries its seconds: the schema's pattern is what clients can rely on.
        assertThat(json.get("createdAt").asText()).matches((String) at("UserSummary/createdAt").get("pattern"));

        wire(new CurrentUserResponse(UUID.randomUUID(), null, null, Role.STUDENT),
            "CurrentUser", "firstName", "lastName");
        wire(new UserPageResponse(List.of(), 0, 20, 0L, 0), "UserPage");
        wire(new ActivationTokenValidationResponse(true), "ActivationTokenValidation");
    }

    @Test
    void the_token_response_is_snake_case_with_a_numeric_expires_in() throws IOException {
        JsonNode json = wire(
            new TokenResponse("jwt", TokenPair.TOKEN_TYPE_BEARER, Duration.ofMinutes(15)), "TokenResponse");
        assertThat(json.get("expires_in").isNumber()).isTrue();
        assertThat(at("TokenResponse/expires_in").get("type")).isEqualTo("number");
        assertThat(at("TokenResponse/token_type").get("const")).isEqualTo(json.get("token_type").asText());
    }

    @Test
    void the_request_schemas_match_the_records_and_their_validation() {
        Map<String, Class<? extends Record>> requests = new LinkedHashMap<>();
        requests.put("LoginRequest", LoginRequest.class);
        requests.put("RegisterRequest", RegisterUserRequest.class);
        requests.put("ResendActivationRequest", ResendActivationRequest.class);
        requests.put("ForgotPasswordRequest", ForgotPasswordRequest.class);
        requests.put("ResetPasswordRequest", ResetPasswordRequest.class);
        requests.put("ActivateAccountRequest", ActivateAccountRequest.class);
        requests.put("CreateUserRequest", CreateUserRequest.class);
        requests.put("ChangeUserRoleRequest", ChangeUserRoleRequest.class);

        List<String> drift = new ArrayList<>();
        requests.forEach((name, type) -> {
            Map<String, Object> node = at(name);
            if (!propertyNames(node).equals(components(type))) {
                drift.add(name + " properties " + propertyNames(node) + " vs " + components(type));
            }
            if (!requiredNames(node).equals(mandatoryComponents(type))) {
                drift.add(name + " required " + requiredNames(node) + " vs " + mandatoryComponents(type));
            }
            for (RecordComponent component : type.getRecordComponents()) {
                Map<String, Object> property = map(map(node, "properties"), component.getName());
                Size size = component.getAccessor().getAnnotation(Size.class);
                if (size != null && !Integer.valueOf(size.max()).equals(property.get("maxLength"))) {
                    drift.add(name + "/" + component.getName() + " maxLength");
                }
                Pattern pattern = component.getAccessor().getAnnotation(Pattern.class);
                if (pattern != null && !pattern.regexp().equals(property.get("pattern"))) {
                    drift.add(name + "/" + component.getName() + " pattern");
                }
            }
        });
        assertThat(drift).isEmpty();
        assertThat(UserProfile.PHONE_REGEX).isEqualTo(at("RegisterRequest/phone").get("pattern"));
    }

    @Test
    void request_bodies_ignore_unknown_properties_so_no_schema_may_forbid_them() throws IOException {
        LoginRequest parsed = mapper.readValue(
            "{\"email\":\"a@example.com\",\"password\":\"x\",\"unexpected\":1}", LoginRequest.class);
        assertThat(parsed.email()).isEqualTo("a@example.com");

        Set<String> forbidding = map(map(spec, "components"), "schemas").entrySet().stream()
            .filter(entry -> Boolean.FALSE.equals(asMap(entry.getValue()).get("additionalProperties")))
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(TreeSet::new));
        assertThat(forbidding).isEmpty();
    }

    @Test
    void the_enums_match_the_domain() {
        List<Object> every = List.of(Role.values()).stream().map(Enum::name).collect(Collectors.toList());
        List<Object> staff = List.of(Role.values()).stream()
            .filter(role -> role != Role.STUDENT).map(Enum::name).collect(Collectors.toList());
        // Public registration accepts every Role value and answers 202 to the ones it will not honour; the
        // staff creation rejects STUDENT with a 400.
        for (String path : List.of("UserRole", "RegisterRequest/role", "ChangeUserRoleRequest/role",
            "UserSummary/role", "CurrentUser/role")) {
            assertThat(list(at(path).get("enum"))).as(path).containsExactlyInAnyOrderElementsOf(every);
        }
        assertThat(list(at("CreateUserRequest/role").get("enum"))).containsExactlyInAnyOrderElementsOf(staff);
        assertThat(list(at("UserStatus").get("enum"))).containsExactlyInAnyOrderElementsOf(
            Arrays.stream(UserStatus.values()).map(Enum::name).collect(Collectors.toList()));
        Map<String, Object> roleParameter = list(operation(LIST_USERS).get("parameters")).stream()
            .map(AuthOpenApiContractTest::asMap).filter(parameter -> "role".equals(parameter.get("name")))
            .findFirst().orElseThrow();
        assertThat(list(resolve(map(roleParameter, "schema")).get("enum"))).containsExactlyInAnyOrderElementsOf(every);
    }

    @Test
    void the_listing_limits_are_the_ones_the_query_enforces() {
        Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();
        list(operation(LIST_USERS).get("parameters")).forEach(parameter ->
            parameters.put((String) asMap(parameter).get("name"), map(parameter, "schema")));
        assertThat(parameters.get("size")).containsEntry("maximum", ListUsersQuery.MAX_PAGE_SIZE)
            .containsEntry("default", ListUsersQuery.DEFAULT_PAGE_SIZE);
        assertThat(parameters.get("q")).containsEntry("maxLength", ListUsersQuery.MAX_SEARCH_LENGTH);
        assertThat(at("UserPage/size")).containsEntry("maximum", ListUsersQuery.MAX_PAGE_SIZE);
    }
}
