package ru.maltsev.primemarketbackend.deposit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DepositAdminApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                deposit_requests,
                deposit_methods
            restart identity cascade
            """);
    }

    @Test
    void backofficeDepositListReturnsUserSummary() throws Exception {
        User user = loadUser("user1@123.123");
        User support = loadUser("sup1@123.123");
        long methodId = insertDepositMethod("Bank transfer", "RUB");

        JsonNode created = createDepositRequest(user, """
            {
              "deposit_method_id": %d,
              "amount": 100.0000
            }
            """.formatted(methodId));

        JsonNode list = listBackofficeDepositRequests(support);
        JsonNode row = list.path("content").get(0);

        assertThat(row.path("public_id").asText()).isEqualTo(created.path("public_id").asText());
        assertThat(row.path("status").asText()).isEqualTo("WAITING_PAYMENT");
        assertThat(row.path("user").path("id").asLong()).isEqualTo(user.getId());
        assertThat(row.path("user").path("username").asText()).isEqualTo(user.getUsername());
        assertThat(row.path("details_issued_at").asText()).isNotBlank();
        assertThat(row.path("created_at").asText()).isNotBlank();
        assertThat(row.path("updated_at").asText()).isNotBlank();
    }

    @Test
    void backofficeDepositListReturnsSnapshotMethodAndCurrencyFields() throws Exception {
        User user = loadUser("user1@123.123");
        User support = loadUser("sup1@123.123");
        long methodId = insertDepositMethod("Bank transfer", "RUB");

        createDepositRequest(user, """
            {
              "deposit_method_id": %d,
              "amount": 250.0000
            }
            """.formatted(methodId));

        jdbcTemplate.update(
            "update deposit_methods set title = ?, currency_code = ? where id = ?",
            "Edited later",
            "USD",
            methodId
        );

        JsonNode list = listBackofficeDepositRequests(support);
        JsonNode row = list.path("content").get(0);

        assertThat(row.path("deposit_method_title").asText()).isEqualTo("Bank transfer");
        assertThat(row.path("currency_code").asText()).isEqualTo("RUB");
    }

    private User loadUser(String email) {
        return userRepository.findWithRolesByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    private JsonNode createDepositRequest(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/deposit-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode listBackofficeDepositRequests(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/backoffice/deposit-requests").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long insertDepositMethod(String title, String currencyCode) {
        Long id = jdbcTemplate.queryForObject(
            """
                insert into deposit_methods (title, currency_code, payment_details, auto_confirmation, is_active)
                values (?, ?, ?::jsonb, false, true)
                returning id
                """,
            Long.class,
            title,
            currencyCode,
            "{\"account\":\"123456\"}"
        );
        if (id == null) {
            throw new IllegalStateException("Failed to insert deposit method");
        }
        return id;
    }
}
