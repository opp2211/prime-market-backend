package ru.maltsev.primemarketbackend.currency.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CurrencyApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void getCurrencyRateIsPublicAndReturnsSeededRate() throws Exception {
        mockMvc.perform(get("/api/currency-rates")
                .queryParam("from", "usd")
                .queryParam("to", "rub"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fromCurrencyCode").value("USD"))
            .andExpect(jsonPath("$.toCurrencyCode").value("RUB"))
            .andExpect(jsonPath("$.rate").value(92.5))
            .andExpect(jsonPath("$.source").value("system"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void getCurrencyRateRequiresFromQueryParameter() throws Exception {
        mockMvc.perform(get("/api/currency-rates")
                .queryParam("to", "RUB"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.detail").value("Query parameter 'from' is required"));
    }
}
