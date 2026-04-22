package ru.maltsev.primemarketbackend.money.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MoneyAreaApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private final AtomicLong testRefIdSequence = new AtomicLong(1000L);

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                withdrawal_requests,
                payout_profiles,
                deposit_requests,
                deposit_methods,
                user_account_txs,
                user_accounts
            restart identity cascade
            """);
    }

    @Test
    void walletsEndpointReturnsAllCurrenciesSortedWithNonZeroFirstAndAvailableCalculated() throws Exception {
        User user = loadUser("user1@123.123");
        fundWallet(user, "USD", "1000.0000");
        long methodId = loadWithdrawalMethodId("BINANCE_UID", "USD");

        createWithdrawal(user, """
            {
              "currency_code": "USD",
              "withdrawal_method_id": %d,
              "amount": 250.0000,
              "requisites": {
                "uid": "778899"
              }
            }
            """.formatted(methodId));

        JsonNode wallets = getWallets(user);
        assertThat(wallets.path("items")).hasSizeGreaterThanOrEqualTo(6);
        assertThat(wallets.path("items").get(0).path("currency_code").asText()).isEqualTo("USD");
        assertThat(wallets.path("items").get(0).path("balance").decimalValue()).isEqualByComparingTo("1000.0000");
        assertThat(wallets.path("items").get(0).path("reserved").decimalValue()).isEqualByComparingTo("250.0000");
        assertThat(wallets.path("items").get(0).path("available").decimalValue()).isEqualByComparingTo("750.0000");
        assertThat(wallets.path("items").get(1).path("balance").decimalValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    void payoutProfilesCrudDefaultAndOwnershipChecksWork() throws Exception {
        User user = loadUser("user1@123.123");
        User otherUser = loadUser("user2@123.123");
        long methodId = loadWithdrawalMethodId("SBP", "RUB");

        JsonNode firstProfile = createPayoutProfile(user, """
            {
              "withdrawal_method_id": %d,
              "title": "Main SBP",
              "requisites": {
                "phoneNumber": "+79990001122",
                "bankName": "T-Bank",
                "recipientName": "Ivan Ivanov"
              },
              "is_default": false
            }
            """.formatted(methodId));
        JsonNode secondProfile = createPayoutProfile(user, """
            {
              "withdrawal_method_id": %d,
              "title": "Reserve SBP",
              "requisites": {
                "phoneNumber": "+79990002233",
                "bankName": "Sber",
                "recipientName": "Ivan Ivanov"
              },
              "is_default": false
            }
            """.formatted(methodId));

        assertThat(firstProfile.path("is_default").asBoolean()).isTrue();
        assertThat(secondProfile.path("is_default").asBoolean()).isFalse();

        JsonNode listed = listPayoutProfiles(user);
        assertThat(listed.get(0).path("public_id").asText()).isEqualTo(firstProfile.path("public_id").asText());

        JsonNode updated = updatePayoutProfile(user, firstProfile.path("public_id").asText(), """
            {
              "title": "Updated SBP",
              "requisites": {
                "phoneNumber": "+79995554433",
                "bankName": "Alfa",
                "recipientName": "Ivan Ivanov"
              }
            }
            """);
        assertThat(updated.path("title").asText()).isEqualTo("Updated SBP");
        assertThat(updated.path("requisites").path("bankName").asText()).isEqualTo("Alfa");

        JsonNode madeDefault = makeDefaultPayoutProfile(user, secondProfile.path("public_id").asText());
        assertThat(madeDefault.path("is_default").asBoolean()).isTrue();
        JsonNode listedAfterDefault = listPayoutProfiles(user);
        assertThat(listedAfterDefault.get(0).path("public_id").asText())
            .isEqualTo(secondProfile.path("public_id").asText());

        mockMvc.perform(patch("/api/payout-profiles/{publicId}", firstProfile.path("public_id").asText())
                .with(auth(otherUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Hacked"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PAYOUT_PROFILE_NOT_FOUND"));

        mockMvc.perform(delete("/api/payout-profiles/{publicId}", secondProfile.path("public_id").asText())
                .with(auth(otherUser)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PAYOUT_PROFILE_NOT_FOUND"));

        mockMvc.perform(delete("/api/payout-profiles/{publicId}", secondProfile.path("public_id").asText())
                .with(auth(user)))
            .andExpect(status().isNoContent());

        JsonNode listedAfterDelete = listPayoutProfiles(user);
        assertThat(listedAfterDelete).hasSize(1);
        assertThat(listedAfterDelete.get(0).path("public_id").asText()).isEqualTo(firstProfile.path("public_id").asText());
    }

    @Test
    void withdrawalCreateReservesFundsAndValidatesMethodAmountAndRequisites() throws Exception {
        User user = loadUser("user1@123.123");
        fundWallet(user, "RUB", "1200.0000");
        long sbpMethodId = loadWithdrawalMethodId("SBP", "RUB");
        long usdtMethodId = loadWithdrawalMethodId("BINANCE_UID", "USD");

        mockMvc.perform(post("/api/withdrawal-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "currency_code": "RUB",
                      "withdrawal_method_id": %d,
                      "amount": 499.9999,
                      "requisites": {
                        "phoneNumber": "+79990001122",
                        "bankName": "T-Bank",
                        "recipientName": "Ivan Ivanov"
                      }
                    }
                    """.formatted(sbpMethodId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/withdrawal-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "currency_code": "RUB",
                      "withdrawal_method_id": %d,
                      "amount": 600.0000,
                      "requisites": {
                        "uid": "12345"
                      }
                    }
                    """.formatted(usdtMethodId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/withdrawal-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "currency_code": "RUB",
                      "withdrawal_method_id": %d,
                      "amount": 600.0000,
                      "requisites": {
                        "phoneNumber": "+79990001122"
                      }
                    }
                    """.formatted(sbpMethodId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        JsonNode created = createWithdrawal(user, """
            {
              "currency_code": "RUB",
              "withdrawal_method_id": %d,
              "amount": 600.0000,
              "requisites": {
                "phoneNumber": "+79990001122",
                "bankName": "T-Bank",
                "recipientName": "Ivan Ivanov"
              }
            }
            """.formatted(sbpMethodId));

        assertThat(created.path("status").asText()).isEqualTo("OPEN");
        UserAccount wallet = userAccountRepository.findByUserIdAndCurrencyCode(user.getId(), "RUB").orElseThrow();
        assertThat(wallet.getReserved()).isEqualByComparingTo("600.0000");
        assertThat(wallet.getBalance()).isEqualByComparingTo("1200.0000");
    }

    @Test
    void withdrawalCancelWorksOnlyForOpenAndReleasesReserve() throws Exception {
        User user = loadUser("user1@123.123");
        User support = loadUser("sup1@123.123");
        fundWallet(user, "USD", "900.0000");
        long methodId = loadWithdrawalMethodId("BINANCE_UID", "USD");

        JsonNode created = createWithdrawal(user, """
            {
              "currency_code": "USD",
              "withdrawal_method_id": %d,
              "amount": 300.0000,
              "requisites": {
                "uid": "11223344"
              }
            }
            """.formatted(methodId));

        JsonNode cancelled = cancelWithdrawal(user, created.path("public_id").asText());
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(loadReserved(user.getId(), "USD")).isEqualByComparingTo("0.0000");

        JsonNode secondCreated = createWithdrawal(user, """
            {
              "currency_code": "USD",
              "withdrawal_method_id": %d,
              "amount": 200.0000,
              "requisites": {
                "uid": "55667788"
              }
            }
            """.formatted(methodId));
        takeWithdrawal(support, secondCreated.path("public_id").asText());

        mockMvc.perform(post("/api/withdrawal-requests/{publicId}/cancel", secondCreated.path("public_id").asText())
                .with(auth(user)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void withdrawalTakeRejectConfirmStateMachineReleasesOrDebitsExactlyOnceAndCreatesTxOnce() throws Exception {
        User user = loadUser("user1@123.123");
        User support = loadUser("sup1@123.123");
        fundWallet(user, "USD", "1000.0000");
        long methodId = loadWithdrawalMethodId("BINANCE_UID", "USD");

        JsonNode rejectCandidate = createWithdrawal(user, """
            {
              "currency_code": "USD",
              "withdrawal_method_id": %d,
              "amount": 150.0000,
              "requisites": {
                "uid": "111111"
              }
            }
            """.formatted(methodId));
        takeWithdrawal(support, rejectCandidate.path("public_id").asText());
        JsonNode rejected = rejectWithdrawal(support, rejectCandidate.path("public_id").asText(), """
            {
              "rejection_reason": "Manual review failed",
              "operator_comment": "Bad requisites"
            }
            """);
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(loadReserved(user.getId(), "USD")).isEqualByComparingTo("0.0000");

        JsonNode confirmCandidate = createWithdrawal(user, """
            {
              "currency_code": "USD",
              "withdrawal_method_id": %d,
              "amount": 600.0000,
              "requisites": {
                "uid": "222222"
              }
            }
            """.formatted(methodId));
        JsonNode taken = takeWithdrawal(support, confirmCandidate.path("public_id").asText());
        assertThat(taken.path("status").asText()).isEqualTo("PROCESSING");

        JsonNode confirmed = confirmWithdrawal(support, confirmCandidate.path("public_id").asText(), """
            {
              "actual_payout_amount": 590.0000,
              "operator_comment": "Rounded by operator"
            }
            """);
        assertThat(confirmed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(confirmed.path("actual_payout_amount").decimalValue()).isEqualByComparingTo("590.0000");
        assertThat(loadReserved(user.getId(), "USD")).isEqualByComparingTo("0.0000");
        assertThat(loadBalance(user.getId(), "USD")).isEqualByComparingTo("400.0000");
        assertThat(loadWithdrawalTxCount(confirmCandidate.path("public_id").asText())).isEqualTo(1);
        assertThat(loadWithdrawalTxAmount(confirmCandidate.path("public_id").asText())).isEqualByComparingTo("-600.0000");

        JsonNode confirmedAgain = confirmWithdrawal(support, confirmCandidate.path("public_id").asText(), """
            {
              "actual_payout_amount": 590.0000
            }
            """);
        assertThat(confirmedAgain.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(loadBalance(user.getId(), "USD")).isEqualByComparingTo("400.0000");
        assertThat(loadWithdrawalTxCount(confirmCandidate.path("public_id").asText())).isEqualTo(1);

        JsonNode txHistory = getWalletTransactions(user, "WITHDRAWAL");
        assertThat(txHistory.path("content").get(0).path("label").asText()).isEqualTo("Withdrawal via Binance UID");
        assertThat(txHistory.path("content").get(0).path("ref_public_id").asText())
            .isEqualTo(confirmCandidate.path("public_id").asText());
    }

    @Test
    void depositRequestSnapshotsSurviveDepositMethodEdits() throws Exception {
        User user = loadUser("user1@123.123");
        long methodId = insertDepositMethod("Bank transfer", "RUB");

        JsonNode created = createDepositRequest(user, """
            {
              "deposit_method_id": %d,
              "amount": 100.0000
            }
            """.formatted(methodId));
        assertThat(created.path("deposit_method_title").asText()).isEqualTo("Bank transfer");
        assertThat(created.path("currency_code").asText()).isEqualTo("RUB");

        jdbcTemplate.update(
            "update deposit_methods set title = ?, currency_code = ? where id = ?",
            "Edited later",
            "USD",
            methodId
        );

        JsonNode detail = getDepositRequest(user, created.path("public_id").asText());
        JsonNode list = listDepositRequests(user);
        assertThat(detail.path("deposit_method_title").asText()).isEqualTo("Bank transfer");
        assertThat(detail.path("currency_code").asText()).isEqualTo("RUB");
        assertThat(list.path("content").get(0).path("deposit_method_title").asText()).isEqualTo("Bank transfer");
        assertThat(list.path("content").get(0).path("currency_code").asText()).isEqualTo("RUB");
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

    private JsonNode getWallets(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/me").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode listPayoutProfiles(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/payout-profiles").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createPayoutProfile(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payout-profiles")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode updatePayoutProfile(User user, String publicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/payout-profiles/{publicId}", publicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode makeDefaultPayoutProfile(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payout-profiles/{publicId}/make-default", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createWithdrawal(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/withdrawal-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode cancelWithdrawal(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/withdrawal-requests/{publicId}/cancel", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode takeWithdrawal(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/withdrawal-requests/{publicId}/take", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode rejectWithdrawal(User user, String publicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/withdrawal-requests/{publicId}/reject", publicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode confirmWithdrawal(User user, String publicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/withdrawal-requests/{publicId}/confirm", publicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getWalletTransactions(User user, String type) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/me/txs")
                .with(auth(user))
                .queryParam("type", type))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
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

    private JsonNode getDepositRequest(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/deposit-requests/{publicId}", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode listDepositRequests(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/deposit-requests").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void fundWallet(User user, String currencyCode, String amount) {
        UserAccount account = userAccountService.getOrCreateAccount(user.getId(), currencyCode);
        jdbcTemplate.update(
            """
                insert into user_account_txs (user_account_id, amount, type, ref_type, ref_id)
                values (?, ?, ?, ?, ?)
                """,
            account.getId(),
            new BigDecimal(amount),
            "TEST_TOP_UP",
            "TEST",
            testRefIdSequence.getAndIncrement()
        );
    }

    private long loadWithdrawalMethodId(String code, String currencyCode) {
        return jdbcTemplate.queryForObject(
            "select id from withdrawal_methods where code = ? and currency_code = ?",
            Long.class,
            code,
            currencyCode
        );
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

    private BigDecimal loadReserved(Long userId, String currencyCode) {
        return jdbcTemplate.queryForObject(
            """
                select reserved
                from user_accounts
                where user_id = ?
                  and currency_code = ?
                """,
            BigDecimal.class,
            userId,
            currencyCode
        );
    }

    private BigDecimal loadBalance(Long userId, String currencyCode) {
        return jdbcTemplate.queryForObject(
            """
                select balance
                from user_accounts
                where user_id = ?
                  and currency_code = ?
                """,
            BigDecimal.class,
            userId,
            currencyCode
        );
    }

    private int loadWithdrawalTxCount(String withdrawalPublicId) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from user_account_txs tx
                join withdrawal_requests wr
                  on wr.id = tx.ref_id
                where tx.ref_type = 'WITHDRAWAL_REQUEST'
                  and wr.public_id = ?
                """,
            Integer.class,
            UUID.fromString(withdrawalPublicId)
        );
    }

    private BigDecimal loadWithdrawalTxAmount(String withdrawalPublicId) {
        return jdbcTemplate.queryForObject(
            """
                select tx.amount
                from user_account_txs tx
                join withdrawal_requests wr
                  on wr.id = tx.ref_id
                where tx.ref_type = 'WITHDRAWAL_REQUEST'
                  and wr.public_id = ?
                """,
            BigDecimal.class,
            UUID.fromString(withdrawalPublicId)
        );
    }
}
