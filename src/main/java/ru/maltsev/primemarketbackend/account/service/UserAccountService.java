package ru.maltsev.primemarketbackend.account.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.api.dto.WalletItemResponse;
import ru.maltsev.primemarketbackend.account.api.dto.WalletTransactionResponse;
import ru.maltsev.primemarketbackend.account.api.dto.WalletWorkItemResponse;
import ru.maltsev.primemarketbackend.account.api.dto.WalletWorkSummaryResponse;
import ru.maltsev.primemarketbackend.account.api.dto.WalletsResponse;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxCriteriaRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxReadRow;
import ru.maltsev.primemarketbackend.currency.domain.Currency;
import ru.maltsev.primemarketbackend.currency.domain.UserCurrencyConversion;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.currency.repository.UserCurrencyConversionRepository;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;
import ru.maltsev.primemarketbackend.deposit.repository.DepositRequestRepository;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldRepository;
import ru.maltsev.primemarketbackend.order.repository.WalletReserveReadRow;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequestStatus;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalRequestRepository;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private static final BigDecimal ZERO_ACCOUNT_MONEY = BigDecimal.valueOf(0L, 4);

    private final UserAccountRepository userAccountRepository;
    private final UserAccountTxCriteriaRepository userAccountTxCriteriaRepository;
    private final CurrencyRepository currencyRepository;
    private final UserCurrencyConversionRepository userCurrencyConversionRepository;
    private final DepositRequestRepository depositRequestRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final UserAccountHoldRepository userAccountHoldRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public WalletsResponse getWallets(Long userId) {
        Map<String, UserAccount> accountsByCurrency = new HashMap<>();
        for (UserAccount account : userAccountRepository.findAllByUserId(userId)) {
            accountsByCurrency.put(account.getCurrencyCode().toUpperCase(Locale.ROOT), account);
        }

        List<WalletItemResponse> items = currencyRepository.findAllByActiveTrueOrderBySortOrderAscCodeAsc().stream()
            .map(currency -> toWalletItem(currency, accountsByCurrency.get(currency.getCode().toUpperCase(Locale.ROOT))))
            .sorted(Comparator.comparing(UserAccountService::isZeroWallet))
            .toList();

        return new WalletsResponse(items);
    }

    @Transactional(readOnly = true)
    public WalletWorkSummaryResponse getWalletWorkSummary(Long userId) {
        List<WalletWorkItemResponse> reserves = new ArrayList<>();
        userAccountHoldRepository.findActiveWalletReserveRowsByUserId(userId).stream()
            .map(this::toHoldReserveItem)
            .forEach(reserves::add);
        withdrawalRequestRepository.findTop10ByUserIdAndStatusInOrderByCreatedAtDesc(
                userId,
                EnumSet.of(WithdrawalRequestStatus.OPEN, WithdrawalRequestStatus.PROCESSING)
            )
            .stream()
            .map(this::toWithdrawalReserveItem)
            .forEach(reserves::add);

        reserves.sort(Comparator.comparing(
            WalletWorkItemResponse::createdAt,
            Comparator.nullsLast(Comparator.reverseOrder())
        ));

        List<WalletWorkItemResponse> pendingDeposits = depositRequestRepository
            .findTop5ByUserIdAndStatusInOrderByCreatedAtDesc(
                userId,
                EnumSet.of(
                    DepositRequestStatus.PENDING_DETAILS,
                    DepositRequestStatus.WAITING_PAYMENT,
                    DepositRequestStatus.PAYMENT_VERIFICATION
                )
            )
            .stream()
            .map(this::toPendingDepositItem)
            .toList();

        return new WalletWorkSummaryResponse(reserves, pendingDeposits);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getUserAccountTxs(
            Long userId,
            List<String> currency,
            List<String> type,
            String query,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Page<UserAccountTxReadRow> page = userAccountTxCriteriaRepository.findUserAccountTxs(
                userId,
                currency,
                type,
                query,
                from,
                to,
                pageable
        );
        TransactionReferenceContext context = buildReferenceContext(page.getContent());
        return page.map(row -> toWalletTransaction(row, context));
    }

    @Transactional
    public UserAccount getOrCreateAccount(Long userId, String currencyCode) {
        return userAccountRepository.findByUserIdAndCurrencyCode(userId, currencyCode)
            .orElseGet(() -> createAccount(userId, currencyCode));
    }

    @Transactional
    public UserAccount getOrCreateAccountForUpdate(Long userId, String currencyCode) {
        return userAccountRepository.findByUserIdAndCurrencyCodeIgnoreCase(userId, currencyCode)
            .orElseGet(() -> createAccount(userId, currencyCode));
    }

    private UserAccount createAccount(Long userId, String currencyCode) {
        User user = userRepository.getReferenceById(userId);
        UserAccount account = new UserAccount(user, currencyCode);
        try {
            return userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            return userAccountRepository.findByUserIdAndCurrencyCodeIgnoreCase(userId, currencyCode)
                .orElseThrow(() -> ex);
        }
    }

    private WalletItemResponse toWalletItem(Currency currency, UserAccount account) {
        if (account == null) {
            return new WalletItemResponse(
                currency.getCode(),
                currency.getTitle(),
                false,
                ZERO_ACCOUNT_MONEY,
                ZERO_ACCOUNT_MONEY,
                ZERO_ACCOUNT_MONEY
            );
        }

        return new WalletItemResponse(
            currency.getCode(),
            currency.getTitle(),
            true,
            account.getBalance(),
            account.getReserved(),
            account.available()
        );
    }

    private static boolean isZeroWallet(WalletItemResponse wallet) {
        return wallet.balance().signum() == 0 && wallet.reserved().signum() == 0;
    }

    private WalletWorkItemResponse toHoldReserveItem(WalletReserveReadRow row) {
        String sourceType = normalizeSourceType(row.sourceType());
        String title = switch (sourceType) {
            case "ORDER" -> "Заказ " + displayCode(row.refCode());
            case "OFFER" -> "Резерв по предложению";
            default -> prettify(row.title());
        };

        return new WalletWorkItemResponse(
            sourceType,
            row.refCode(),
            row.refId(),
            title,
            firstNonBlank(row.title(), row.description()),
            row.amount(),
            row.currencyCode(),
            row.status(),
            row.createdAt()
        );
    }

    private WalletWorkItemResponse toWithdrawalReserveItem(WithdrawalRequest request) {
        return new WalletWorkItemResponse(
            "WITHDRAWAL_REQUEST",
            request.getPublicCode(),
            request.getId(),
            "Заявка на вывод " + displayCode(request.getPublicCode()),
            firstNonBlank(request.getWithdrawalMethodTitleSnapshot(), request.getStatus().name()),
            request.getAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getStatus().name(),
            request.getCreatedAt()
        );
    }

    private WalletWorkItemResponse toPendingDepositItem(DepositRequest request) {
        return new WalletWorkItemResponse(
            "DEPOSIT_REQUEST",
            request.getPublicCode(),
            request.getId(),
            "Пополнение " + displayCode(request.getPublicCode()),
            firstNonBlank(request.getDepositMethodTitleSnapshot(), request.getStatus().name()),
            request.getAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getStatus().name(),
            request.getCreatedAt()
        );
    }

    private TransactionReferenceContext buildReferenceContext(List<UserAccountTxReadRow> rows) {
        Set<Long> depositIds = rows.stream()
            .filter(row -> "DEPOSIT_REQUEST".equals(row.refType()))
            .map(UserAccountTxReadRow::refId)
            .collect(java.util.stream.Collectors.toSet());
        Set<Long> withdrawalIds = rows.stream()
            .filter(row -> "WITHDRAWAL_REQUEST".equals(row.refType()))
            .map(UserAccountTxReadRow::refId)
            .collect(java.util.stream.Collectors.toSet());
        Set<Long> orderIds = rows.stream()
            .filter(row -> row.refType() != null && row.refType().startsWith("ORDER_"))
            .map(UserAccountTxReadRow::refId)
            .collect(java.util.stream.Collectors.toSet());
        Set<Long> conversionIds = rows.stream()
            .filter(row -> "USER_CURRENCY_CONVERSION".equals(row.refType()))
            .map(UserAccountTxReadRow::refId)
            .collect(java.util.stream.Collectors.toSet());

        Map<Long, DepositRequest> depositsById = new HashMap<>();
        depositRequestRepository.findAllById(depositIds).forEach(request -> depositsById.put(request.getId(), request));

        Map<Long, WithdrawalRequest> withdrawalsById = new HashMap<>();
        withdrawalRequestRepository.findAllById(withdrawalIds)
            .forEach(request -> withdrawalsById.put(request.getId(), request));

        Map<Long, Order> ordersById = new HashMap<>();
        orderRepository.findAllById(orderIds).forEach(order -> ordersById.put(order.getId(), order));
        Map<Long, UserCurrencyConversion> conversionsById = new HashMap<>();
        userCurrencyConversionRepository.findAllById(conversionIds)
            .forEach(conversion -> conversionsById.put(conversion.getId(), conversion));

        return new TransactionReferenceContext(depositsById, withdrawalsById, ordersById, conversionsById);
    }

    private WalletTransactionResponse toWalletTransaction(
        UserAccountTxReadRow row,
        TransactionReferenceContext context
    ) {
        return new WalletTransactionResponse(
            row.publicCode(),
            row.amount(),
            row.currencyCode(),
            row.txType(),
            resolveLabel(row, context),
            row.refType(),
            resolveRefCode(row, context),
            row.createdAt()
        );
    }

    private String resolveLabel(UserAccountTxReadRow row, TransactionReferenceContext context) {
        if ("DEPOSIT".equals(row.txType())) {
            DepositRequest request = context.depositsById().get(row.refId());
            if (request != null && request.getDepositMethodTitleSnapshot() != null
                && !request.getDepositMethodTitleSnapshot().isBlank()) {
                return "Deposit via " + request.getDepositMethodTitleSnapshot();
            }
            return "Deposit";
        }
        if ("WITHDRAWAL".equals(row.txType())) {
            WithdrawalRequest request = context.withdrawalsById().get(row.refId());
            if (request != null && request.getWithdrawalMethodTitleSnapshot() != null
                && !request.getWithdrawalMethodTitleSnapshot().isBlank()) {
                return "Withdrawal via " + request.getWithdrawalMethodTitleSnapshot();
            }
            return "Withdrawal";
        }
        if ("USER_CURRENCY_CONVERSION".equals(row.refType())) {
            UserCurrencyConversion conversion = context.conversionsById().get(row.refId());
            if (conversion != null) {
                return "Currency conversion %s to %s".formatted(
                    conversion.getFromCurrencyCode(),
                    conversion.getToCurrencyCode()
                );
            }
            return "Currency conversion";
        }
        return switch (row.txType()) {
            case "ORDER_SETTLEMENT_DEBIT" -> "Order settlement debit";
            case "ORDER_SELLER_PAYOUT" -> "Order seller payout";
            default -> prettify(row.txType());
        };
    }

    private String resolveRefCode(UserAccountTxReadRow row, TransactionReferenceContext context) {
        if ("DEPOSIT_REQUEST".equals(row.refType())) {
            DepositRequest request = context.depositsById().get(row.refId());
            return request == null ? null : request.getPublicCode();
        }
        if ("WITHDRAWAL_REQUEST".equals(row.refType())) {
            WithdrawalRequest request = context.withdrawalsById().get(row.refId());
            return request == null ? null : request.getPublicCode();
        }
        if (row.refType() != null && row.refType().startsWith("ORDER_")) {
            Order order = context.ordersById().get(row.refId());
            return order == null ? null : order.getPublicCode();
        }
        if ("USER_CURRENCY_CONVERSION".equals(row.refType())) {
            UserCurrencyConversion conversion = context.conversionsById().get(row.refId());
            return conversion == null ? null : conversion.getPublicCode();
        }
        return null;
    }

    private String prettify(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "UNKNOWN";
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private String displayCode(String publicCode) {
        if (publicCode == null) {
            return "";
        }
        return publicCode;
    }

    private record TransactionReferenceContext(
        Map<Long, DepositRequest> depositsById,
        Map<Long, WithdrawalRequest> withdrawalsById,
        Map<Long, Order> ordersById,
        Map<Long, UserCurrencyConversion> conversionsById
    ) {
    }
}
