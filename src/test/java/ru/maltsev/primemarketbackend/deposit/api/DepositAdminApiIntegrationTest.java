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
                treasury_transactions,
                treasury_accounts,
                money_operation_events,
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

    @Test
    void backofficeDepositActionsWriteUnifiedMoneyAuditEventsAndOperatorSnapshot() throws Exception {
        User user = loadUser("user1@123.123");
        User support = loadUser("sup1@123.123");
        long methodId = insertDepositMethod("Manual SBP", "RUB", null);

        JsonNode created = createDepositRequest(user, """
            {
              "deposit_method_id": %d,
              "amount": 1000.0000
            }
            """.formatted(methodId));
        String publicId = created.path("public_id").asText();
        assertThat(created.path("status").asText()).isEqualTo("PENDING_DETAILS");

        JsonNode issued = issueDepositDetails(support, publicId, """
            {
              "payment_details": "T-Bank card 2200 0000 0000 0000",
              "operator_comment": "Issued personal T-Bank card"
            }
            """);
        assertThat(issued.path("status").asText()).isEqualTo("WAITING_PAYMENT");
        assertThat(issued.path("details_issued_by_user_id").asLong()).isEqualTo(support.getId());
        assertThat(issued.path("operator_comment").asText()).isEqualTo("Issued personal T-Bank card");

        String treasuryAccountPublicId = insertTreasuryAccount("tbank-rub-deposits", "T-Bank RUB deposits", "RUB");
        markDepositPaid(user, publicId);
        JsonNode confirmed = confirmDeposit(support, publicId, """
            {
              "confirmation_reference": "tbank-incoming-100500",
              "operator_comment": "Incoming payment matched by amount",
              "treasury_account_public_id": "%s"
            }
            """.formatted(treasuryAccountPublicId));
        assertThat(confirmed.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.path("confirmed_by_user_id").asLong()).isEqualTo(support.getId());
        assertThat(confirmed.path("confirmation_reference").asText()).isEqualTo("tbank-incoming-100500");
        assertThat(confirmed.path("operator_comment").asText()).isEqualTo("Incoming payment matched by amount");
        assertThat(confirmed.path("treasury_transactions")).hasSize(1);
        assertThat(confirmed.path("treasury_transactions").get(0).path("amount").decimalValue())
            .isEqualByComparingTo("1000.0000");
        assertThat(confirmed.path("treasury_transactions").get(0).path("treasury_account_code").asText())
            .isEqualTo("TBANK-RUB-DEPOSITS");
        assertThat(confirmed.path("events")).hasSize(4);
        assertThat(confirmed.path("events").get(0).path("event_type").asText()).isEqualTo("DEPOSIT_CREATED");
        assertThat(confirmed.path("events").get(1).path("event_type").asText()).isEqualTo("DEPOSIT_DETAILS_ISSUED");
        assertThat(confirmed.path("events").get(1).path("actor_type").asText()).isEqualTo("OPERATOR");
        assertThat(confirmed.path("events").get(1).path("actor_user_id").asLong()).isEqualTo(support.getId());
        assertThat(confirmed.path("events").get(3).path("event_type").asText()).isEqualTo("DEPOSIT_CONFIRMED");
        assertThat(confirmed.path("events").get(3).path("operator_note").asText())
            .isEqualTo("Incoming payment matched by amount");

        assertThat(loadMoneyEventTypes("DEPOSIT_REQUEST", publicId))
            .containsExactly(
                "DEPOSIT_CREATED",
                "DEPOSIT_DETAILS_ISSUED",
                "DEPOSIT_USER_MARKED_PAID",
                "DEPOSIT_CONFIRMED"
            );
        assertThat(loadTreasuryBalance(treasuryAccountPublicId)).isEqualByComparingTo("1000.0000");
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

    private JsonNode issueDepositDetails(User user, String publicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/deposit-requests/{publicId}/issue-details", publicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode markDepositPaid(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/deposit-requests/{publicId}/mark-paid", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode confirmDeposit(User user, String publicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/deposit-requests/{publicId}/confirm", publicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
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
        return insertDepositMethod(title, currencyCode, "{\"account\":\"123456\"}");
    }

    private long insertDepositMethod(String title, String currencyCode, String paymentDetails) {
        Long id = jdbcTemplate.queryForObject(
            """
                insert into deposit_methods (title, currency_code, payment_details, auto_confirmation, is_active)
                values (?, ?, ?::jsonb, false, true)
                returning id
                """,
            Long.class,
            title,
            currencyCode,
            paymentDetails
        );
        if (id == null) {
            throw new IllegalStateException("Failed to insert deposit method");
        }
        return id;
    }

    private java.util.List<String> loadMoneyEventTypes(String operationType, String publicId) {
        return jdbcTemplate.queryForList(
            """
                select event_type
                from money_operation_events
                where operation_type = ?
                  and operation_public_id = ?::uuid
                order by created_at, id
                """,
            String.class,
            operationType,
            publicId
        );
    }

    private String insertTreasuryAccount(String code, String title, String currencyCode) {
        java.util.UUID publicId = jdbcTemplate.queryForObject(
            """
                insert into treasury_accounts (code, title, currency_code, account_type)
                values (?, ?, ?, 'BANK_CARD')
                returning public_id
                """,
            java.util.UUID.class,
            code.toUpperCase(java.util.Locale.ROOT),
            title,
            currencyCode
        );
        if (publicId == null) {
            throw new IllegalStateException("Failed to insert treasury account");
        }
        return publicId.toString();
    }

    private java.math.BigDecimal loadTreasuryBalance(String publicId) {
        return jdbcTemplate.queryForObject(
            "select balance from treasury_accounts where public_id = ?::uuid",
            java.math.BigDecimal.class,
            publicId
        );
    }
}
