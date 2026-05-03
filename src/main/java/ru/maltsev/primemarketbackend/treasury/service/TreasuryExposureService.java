package ru.maltsev.primemarketbackend.treasury.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryExposureResponse;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryExposureRowResponse;

@Service
@RequiredArgsConstructor
public class TreasuryExposureService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public TreasuryExposureResponse buildExposureReport() {
        List<TreasuryExposureRowResponse> rows = jdbcTemplate.query("""
            with currency_set as (
                select code as currency_code from currencies where is_active = true
                union
                select currency_code from treasury_accounts
                union
                select currency_code from user_accounts
                union
                select currency_code from platform_accounts
            ),
            treasury_totals as (
                select currency_code, coalesce(sum(balance), 0) as balance
                from treasury_accounts
                group by currency_code
            ),
            user_totals as (
                select currency_code,
                       coalesce(sum(balance), 0) as balance,
                       coalesce(sum(reserved), 0) as reserved
                from user_accounts
                group by currency_code
            ),
            platform_totals as (
                select currency_code, coalesce(sum(balance), 0) as balance
                from platform_accounts
                group by currency_code
            )
            select cs.currency_code,
                   coalesce(tt.balance, 0) as treasury_balance,
                   coalesce(ut.balance, 0) as user_balance,
                   coalesce(ut.reserved, 0) as user_reserved,
                   coalesce(pt.balance, 0) as platform_balance
            from currency_set cs
            left join treasury_totals tt on tt.currency_code = cs.currency_code
            left join user_totals ut on ut.currency_code = cs.currency_code
            left join platform_totals pt on pt.currency_code = cs.currency_code
            order by cs.currency_code
            """, (rs, rowNum) -> {
            BigDecimal treasuryBalance = rs.getBigDecimal("treasury_balance");
            BigDecimal userBalance = rs.getBigDecimal("user_balance");
            BigDecimal userReserved = rs.getBigDecimal("user_reserved");
            BigDecimal platformBalance = rs.getBigDecimal("platform_balance");
            BigDecimal expectedTreasuryBalance = userBalance.add(platformBalance);
            return new TreasuryExposureRowResponse(
                rs.getString("currency_code"),
                treasuryBalance,
                userBalance,
                userReserved,
                userBalance.subtract(userReserved),
                platformBalance,
                expectedTreasuryBalance,
                treasuryBalance.subtract(expectedTreasuryBalance)
            );
        });
        return new TreasuryExposureResponse(Instant.now(), rows);
    }
}
