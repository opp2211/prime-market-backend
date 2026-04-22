package ru.maltsev.primemarketbackend;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;

@ActiveProfiles("test")
@SpringBootTest
class PrimeMarketBackendApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}
