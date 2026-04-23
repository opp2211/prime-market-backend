package ru.maltsev.primemarketbackend.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false",
        "springdoc.paths-to-match=/api/**"
    }
)
class OpenApiContractArtifactIT extends AbstractPostgresIntegrationTest {
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${local.server.port}")
    private int port;

    @Test
    void writesCommittedOpenApiContractArtifact() throws Exception {
        String rawContract = fetchContract();
        JsonNode contract = objectMapper.readTree(rawContract);

        assertCoreCoverage(contract);
        assertContractClarity(contract);
        writeContract(rawContract);
    }

    private String fetchContract() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private void assertCoreCoverage(JsonNode contract) {
        JsonNode paths = contract.path("paths");
        assertThat(contract.path("openapi").asText()).isNotBlank();
        assertThat(paths.isObject()).isTrue();
        assertThat(paths.has("/api/auth/login")).isTrue();
        assertThat(paths.has("/api/users/me")).isTrue();
        assertThat(paths.has("/api/currencies")).isTrue();
        assertThat(paths.has("/api/wallets/me")).isTrue();
        assertThat(paths.has("/api/wallets/me/txs")).isTrue();
        assertThat(paths.has("/api/deposit-requests")).isTrue();
        assertThat(paths.has("/api/backoffice/deposit-requests")).isTrue();
        assertThat(paths.has("/api/withdrawal-requests")).isTrue();
        assertThat(paths.has("/api/backoffice/withdrawal-requests")).isTrue();
        assertThat(paths.has("/api/notifications")).isTrue();
        assertThat(paths.has("/api/notifications/stream")).isTrue();
        assertThat(paths.has("/api/notifications/unread-count")).isTrue();
        assertThat(paths.has("/api/notifications/{publicId}/read")).isTrue();
        assertThat(paths.has("/api/notifications/read-all")).isTrue();
        assertThat(paths.has("/api/offers")).isTrue();
        assertThat(paths.has("/api/market/offers")).isTrue();
        assertThat(paths.has("/api/market/offers/{offerId}/quote")).isTrue();
        assertThat(paths.has("/api/order-quotes/{quoteId}/refresh")).isTrue();
        assertThat(paths.has("/api/orders")).isTrue();
        assertThat(paths.has("/api/orders/{orderId}")).isTrue();
        assertThat(paths.has("/api/order-requests/{requestId}/approve")).isTrue();
        assertThat(paths.has("/api/orders/{orderId}/dispute")).isTrue();
        assertThat(paths.has("/api/orders/{orderId}/conversations")).isTrue();
        assertThat(paths.has("/api/order-conversations/{conversationId}/messages")).isTrue();
        assertThat(paths.has("/api/backoffice/disputes")).isTrue();
        assertThat(paths.has("/test/public")).isFalse();
        assertThat(contract.path("servers")).hasSize(1);
        assertThat(contract.path("servers").get(0).path("url").asText()).isEqualTo("/");
    }

    private void assertContractClarity(JsonNode contract) {
        JsonNode schemas = contract.path("components").path("schemas");

        assertThat(schemas.has("Item")).isFalse();
        assertThat(schemas.has("Price")).isFalse();
        assertThat(schemas.has("AvailableActions")).isFalse();

        assertThat(refOf(schemas, "MyOrdersResponse", "items", "items"))
            .isEqualTo("#/components/schemas/MyOrderListItem");
        assertThat(refOf(schemas, "MarketOfferListResponse", "items", "items"))
            .isEqualTo("#/components/schemas/MarketOfferListItem");
        assertThat(refOf(schemas, "MarketOfferListItem", "price"))
            .isEqualTo("#/components/schemas/MarketOfferPrice");
        assertThat(refOf(schemas, "OrderDetailsResponse", "price"))
            .isEqualTo("#/components/schemas/OrderPrice");
        assertThat(refOf(schemas, "OrderDetailsResponse", "availableActions"))
            .isEqualTo("#/components/schemas/OrderAvailableActions");
        assertThat(refOf(schemas, "OrderDisputeResponse", "availableActions"))
            .isEqualTo("#/components/schemas/OrderDisputeAvailableActions");
        assertThat(refOf(schemas, "Dispute", "availableActions"))
            .isEqualTo("#/components/schemas/OrderDisputeSummaryAvailableActions");

        assertThat(queryParameterNames(contract, "/api/withdrawal-requests", "get"))
            .containsExactly("status", "page", "size", "sort");
        assertThat(queryParameterNames(contract, "/api/backoffice/withdrawal-requests", "get"))
            .containsExactly("status", "page", "size", "sort");
        assertThat(queryParameterNames(contract, "/api/deposit-requests", "get"))
            .containsExactly("status", "page", "size", "sort");
        assertThat(queryParameterNames(contract, "/api/backoffice/deposit-requests", "get"))
            .containsExactly("status", "page", "size", "sort");
        assertThat(queryParameterNames(contract, "/api/notifications", "get"))
            .containsExactly("isRead", "page", "size", "sort");
        assertThat(queryParameterNames(contract, "/api/wallets/me/txs", "get"))
            .containsExactly("currency", "type", "from", "to", "page", "size", "sort");

        assertThat(queryParameterNames(contract, "/api/withdrawal-requests", "get"))
            .doesNotContain("pageable", "statuses", "currency_code");
        assertThat(queryParameterNames(contract, "/api/backoffice/withdrawal-requests", "get"))
            .doesNotContain("pageable", "statuses", "currency_code");
        assertThat(queryParameterNames(contract, "/api/notifications", "get"))
            .doesNotContain("pageable");
        assertThat(allQueryParameterNames(contract)).doesNotContain("pageable");

        JsonNode userWithdrawalStatus = queryParameter(contract, "/api/withdrawal-requests", "get", "status");
        assertThat(userWithdrawalStatus.path("schema").path("type").asText()).isEqualTo("string");
        assertThat(jsonTextValues(userWithdrawalStatus.path("schema").path("enum")))
            .containsExactly("OPEN", "PROCESSING", "COMPLETED", "CANCELLED", "REJECTED");

        JsonNode backofficeWithdrawalStatus = queryParameter(contract, "/api/backoffice/withdrawal-requests", "get", "status");
        assertThat(backofficeWithdrawalStatus.path("schema").path("type").asText()).isEqualTo("array");
        assertThat(jsonTextValues(backofficeWithdrawalStatus.path("schema").path("items").path("enum")))
            .containsExactly("OPEN", "PROCESSING", "COMPLETED", "CANCELLED", "REJECTED");

        assertThat(queryParameter(contract, "/api/market/offers", "get", "gameSlug").path("required").asBoolean()).isTrue();
        assertThat(queryParameter(contract, "/api/market/offers", "get", "categorySlug").path("required").asBoolean()).isTrue();
        assertThat(queryParameter(contract, "/api/market/offers", "get", "intent").path("required").asBoolean()).isTrue();
        assertThat(queryParameter(contract, "/api/market/offers", "get", "viewerCurrencyCode").path("required").asBoolean()).isTrue();
        assertThat(queryParameter(contract, "/api/market/offers/{offerId}", "get", "intent").path("required").asBoolean()).isTrue();
        assertThat(queryParameter(contract, "/api/market/offers/{offerId}", "get", "viewerCurrencyCode").path("required").asBoolean()).isTrue();

        assertThat(contract.path("paths").path("/api/withdrawal-requests").path("post").path("responses").has("201")).isTrue();
        assertThat(contract.path("paths").path("/api/withdrawal-requests").path("post").path("responses").has("200")).isFalse();
        assertThat(contract.path("paths").path("/api/deposit-requests").path("post").path("responses").has("201")).isTrue();
        assertThat(contract.path("paths").path("/api/deposit-requests").path("post").path("responses").has("200")).isFalse();
    }

    private String refOf(JsonNode schemas, String schemaName, String propertyName) {
        return schemas.path(schemaName).path("properties").path(propertyName).path("$ref").asText();
    }

    private String refOf(JsonNode schemas, String schemaName, String propertyName, String nestedPropertyName) {
        return schemas.path(schemaName)
            .path("properties")
            .path(propertyName)
            .path(nestedPropertyName)
            .path("$ref")
            .asText();
    }

    private List<String> queryParameterNames(JsonNode contract, String path, String method) {
        List<String> names = new ArrayList<>();
        JsonNode parameters = contract.path("paths").path(path).path(method).path("parameters");
        parameters.forEach(parameter -> {
            if ("query".equals(parameter.path("in").asText())) {
                names.add(parameter.path("name").asText());
            }
        });
        return names;
    }

    private List<String> allQueryParameterNames(JsonNode contract) {
        List<String> names = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathEntries = contract.path("paths").fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            Iterator<Map.Entry<String, JsonNode>> methodEntries = pathEntry.getValue().fields();
            while (methodEntries.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodEntries.next();
                methodEntry.getValue().path("parameters").forEach(parameter -> {
                    if ("query".equals(parameter.path("in").asText())) {
                        names.add(parameter.path("name").asText());
                    }
                });
            }
        }
        return names;
    }

    private JsonNode queryParameter(JsonNode contract, String path, String method, String parameterName) {
        for (JsonNode parameter : contract.path("paths").path(path).path(method).path("parameters")) {
            if ("query".equals(parameter.path("in").asText())
                && parameterName.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new IllegalStateException("Missing query parameter '%s' for %s %s".formatted(parameterName, method, path));
    }

    private List<String> jsonTextValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private void writeContract(String rawContract) throws IOException {
        Path output = Path.of(System.getProperty("openapi.contract.output", "api-contract/openapi.json"))
            .toAbsolutePath()
            .normalize();

        Files.createDirectories(output.getParent());
        Files.writeString(output, normalize(rawContract) + "\n", StandardCharsets.UTF_8);
    }

    private String normalize(String rawContract) throws IOException {
        Object contractObject = objectMapper.readValue(rawContract, OBJECT_TYPE);
        Object normalized = normalizeValue(contractObject);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalized);
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }
        return value;
    }
}
