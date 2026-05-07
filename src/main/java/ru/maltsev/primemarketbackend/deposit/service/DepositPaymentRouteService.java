package ru.maltsev.primemarketbackend.deposit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.deposit.api.dto.CreateDepositPaymentRouteRequest;
import ru.maltsev.primemarketbackend.deposit.api.dto.UpdateDepositPaymentRouteRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositMethod;
import ru.maltsev.primemarketbackend.deposit.domain.DepositPaymentRoute;
import ru.maltsev.primemarketbackend.deposit.repository.DepositMethodRepository;
import ru.maltsev.primemarketbackend.deposit.repository.DepositPaymentRouteRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;
import ru.maltsev.primemarketbackend.treasury.repository.TreasuryAccountRepository;

@Service
@RequiredArgsConstructor
public class DepositPaymentRouteService {
    private static final int MONEY_SCALE = 4;

    private final DepositPaymentRouteRepository routeRepository;
    private final DepositMethodRepository depositMethodRepository;
    private final TreasuryAccountRepository treasuryAccountRepository;

    @Transactional(readOnly = true)
    public List<DepositPaymentRoute> listRoutes(Long depositMethodId, Boolean activeOnly) {
        return routeRepository.listRoutes(depositMethodId, Boolean.TRUE.equals(activeOnly));
    }

    @Transactional
    public DepositPaymentRoute createRoute(CreateDepositPaymentRouteRequest request) {
        DepositMethod method = depositMethodRepository.findById(request.depositMethodId())
            .orElseThrow(() -> notFound("DEPOSIT_METHOD_NOT_FOUND", "Deposit method not found"));
        TreasuryAccount treasuryAccount = treasuryAccountRepository.findById(request.treasuryAccountId())
            .orElseThrow(() -> notFound("TREASURY_ACCOUNT_NOT_FOUND", "Treasury account not found"));
        if (!treasuryAccount.isActive()) {
            throw conflict("TREASURY_ACCOUNT_INACTIVE", "Treasury account is inactive");
        }

        validateMinMax(request.minAmount(), request.maxAmount());
        return routeRepository.save(new DepositPaymentRoute(
            method,
            treasuryAccount,
            request.title(),
            request.paymentDetails(),
            normalizeOptionalMoney(request.minAmount()),
            normalizeOptionalMoney(request.maxAmount()),
            request.priority(),
            request.active(),
            request.note()
        ));
    }

    @Transactional
    public DepositPaymentRoute updateRoute(Long id, UpdateDepositPaymentRouteRequest request) {
        DepositPaymentRoute route = routeRepository.findById(id)
            .orElseThrow(() -> notFound("DEPOSIT_PAYMENT_ROUTE_NOT_FOUND", "Deposit payment route not found"));
        TreasuryAccount treasuryAccount = null;
        if (request.treasuryAccountId() != null) {
            treasuryAccount = treasuryAccountRepository.findById(request.treasuryAccountId())
                .orElseThrow(() -> notFound("TREASURY_ACCOUNT_NOT_FOUND", "Treasury account not found"));
        }
        validateMinMax(request.minAmount(), request.maxAmount());
        route.update(
            treasuryAccount,
            request.title(),
            request.paymentDetails(),
            normalizeOptionalMoney(request.minAmount()),
            normalizeOptionalMoney(request.maxAmount()),
            request.priority(),
            request.active(),
            request.note()
        );
        return routeRepository.save(route);
    }

    @Transactional(readOnly = true)
    public DepositPaymentRoute selectRoute(DepositMethod method, BigDecimal amount) {
        return routeRepository.findActiveCandidates(method.getId(), normalizePositiveMoney(amount))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private void validateMinMax(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount != null && maxAmount != null
            && normalizeOptionalMoney(minAmount).compareTo(normalizeOptionalMoney(maxAmount)) > 0) {
            throw validationError("min_amount cannot be greater than max_amount");
        }
    }

    private BigDecimal normalizeOptionalMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return normalizePositiveMoney(amount);
    }

    private BigDecimal normalizePositiveMoney(BigDecimal amount) {
        try {
            BigDecimal normalized = amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0) {
                throw validationError("Amount must be positive");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw validationError("Amount scale must be 4 or less");
        }
    }

    private ApiProblemException validationError(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    private ApiProblemException notFound(String code, String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, code, detail);
    }

    private ApiProblemException conflict(String code, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, detail);
    }
}
