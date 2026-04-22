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
