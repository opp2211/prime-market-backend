package ru.maltsev.primemarketbackend.money.api;

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
class TreasuryBackofficeIntegrationTest extends AbstractPostgresIntegrationTest {
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
                platform_account_transactions,
                withdrawal_payout_plans,
                deposit_payment_instructions,
                deposit_payment_routes,
                treasury_transactions,
                treasury_accounts
            restart identity cascade
            """);
    }

    @Test
    void treasuryAccountsManualTransactionsAndTransfersWork() throws Exception {
        User support = loadUser("sup1@123.123");

        JsonNode rubAccount = createAccount(support, """
            {
              "code": "tbank-rub-main",
              "title": "T-Bank RUB main",
              "currency_code": "RUB",
              "account_type": "BANK_CARD",
              "details": {
                "bank": "T-Bank",
                "cardLast4": "0000"
              }
            }
            """);
        JsonNode usdAccount = createAccount(support, """
            {
              "code": "bybit-usd-main",
              "title": "Bybit USD main",
              "currency_code": "USD",
              "account_type": "EXCHANGE_ACCOUNT"
            }
            """);

        JsonNode manual = createTransaction(support, """
            {
              "treasury_account_public_id": "%s",
              "transaction_type": "MANUAL_IN",
              "amount": 1000.0000,
              "external_reference": "opening-balance",
              "description": "Opening balance"
            }
            """.formatted(rubAccount.path("public_id").asText()));
        assertThat(manual.path("amount").decimalValue()).isEqualByComparingTo("1000.0000");
        assertThat(manual.path("transaction_type").asText()).isEqualTo("MANUAL_IN");

        JsonNode accountsAfterManual = listAccounts(support);
        assertThat(findAccount(accountsAfterManual, "TBANK-RUB-MAIN").path("balance").decimalValue())
            .isEqualByComparingTo("1000.0000");

        JsonNode transfer = createTransfer(support, """
            {
              "from_account_public_id": "%s",
              "to_account_public_id": "%s",
              "from_amount": 100.0000,
              "to_amount": 1.0000,
              "external_reference": "p2p-conversion-1",
              "description": "RUB to USD conversion"
            }
            """.formatted(
                rubAccount.path("public_id").asText(),
                usdAccount.path("public_id").asText()
            ));
        assertThat(transfer.path("outgoing").path("amount").decimalValue()).isEqualByComparingTo("-100.0000");
        assertThat(transfer.path("incoming").path("amount").decimalValue()).isEqualByComparingTo("1.0000");
        assertThat(transfer.path("outgoing").path("group_public_id").asText())
            .isEqualTo(transfer.path("incoming").path("group_public_id").asText());

        JsonNode accountsAfterTransfer = listAccounts(support);
        assertThat(findAccount(accountsAfterTransfer, "TBANK-RUB-MAIN").path("balance").decimalValue())
            .isEqualByComparingTo("900.0000");
        assertThat(findAccount(accountsAfterTransfer, "BYBIT-USD-MAIN").path("balance").decimalValue())
            .isEqualByComparingTo("1.0000");

        JsonNode txs = listTransactions(support);
        assertThat(txs.path("content")).hasSize(3);
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

    private JsonNode createAccount(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/treasury/accounts")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createTransaction(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/treasury/transactions")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createTransfer(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/treasury/transfers")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode listAccounts(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/backoffice/treasury/accounts").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode listTransactions(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/backoffice/treasury/transactions").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findAccount(JsonNode accounts, String code) {
        for (JsonNode account : accounts) {
            if (code.equals(account.path("code").asText())) {
                return account;
            }
        }
        throw new AssertionError("Account not found: " + code);
    }
}
