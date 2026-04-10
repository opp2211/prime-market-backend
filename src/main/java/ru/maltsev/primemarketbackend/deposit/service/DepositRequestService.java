package ru.maltsev.primemarketbackend.deposit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.deposit.api.dto.CreateDepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositMethod;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;
import ru.maltsev.primemarketbackend.deposit.repository.DepositMethodRepository;
import ru.maltsev.primemarketbackend.deposit.repository.DepositRequestRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepositRequestService {
    private static final String TX_TYPE_DEPOSIT = "DEPOSIT";
    private static final String REF_TYPE_DEPOSIT_REQUEST = "DEPOSIT_REQUEST";

    private static final Set<DepositRequestStatus> USER_CANCELLABLE_STATUSES = EnumSet.of(
        DepositRequestStatus.PENDING_DETAILS,
        DepositRequestStatus.WAITING_PAYMENT
    );

    private final DepositRequestRepository depositRequestRepository;
    private final DepositMethodRepository depositMethodRepository;
    private final UserAccountTxRepository userAccountTxRepository;
    private final UserAccountService userAccountService;

    @Transactional
    public DepositRequest create(Long userId, CreateDepositRequest request) {
        DepositMethod method = depositMethodRepository.findByIdAndActiveTrue(request.depositMethodId())
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "DEPOSIT_METHOD_NOT_FOUND",
                "Deposit method not found"
            ));

        DepositRequest depositRequest = new DepositRequest(userId, method, request.amount());
        String paymentDetails = method.getPaymentDetails();
        if (paymentDetails != null && !paymentDetails.isBlank()) {
            depositRequest.startWaitingPayment(paymentDetails);
        } else {
            depositRequest.startPendingDetails();
        }

        return depositRequestRepository.save(depositRequest);
    }

    public DepositRequest getForUser(UUID publicId, Long userId) {
        return depositRequestRepository.findByPublicIdAndUserId(publicId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "DEPOSIT_REQUEST_NOT_FOUND",
                "Deposit request not found"
            ));
    }

    public Page<DepositRequest> listForUser(Long userId, String status, Pageable pageable) {
        DepositRequestStatus parsedStatus = parseStatus(status);
        if (parsedStatus == null) {
            return depositRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return depositRequestRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, parsedStatus, pageable);
    }

    public Page<DepositRequest> listForAdmin(List<String> statuses, Pageable pageable) {
        Set<DepositRequestStatus> parsedStatuses = parseStatuses(statuses);
        if (parsedStatuses == null || parsedStatuses.isEmpty()) {
            return depositRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return depositRequestRepository.findAllByStatusInOrderByCreatedAtDesc(parsedStatuses, pageable);
    }

    @Transactional
    public DepositRequest markPaid(UUID publicId, Long userId) {
        DepositRequest request = getForUser(publicId, userId);
        requireStatus(request, DepositRequestStatus.WAITING_PAYMENT, "mark as paid");
        request.markPaid();
        return depositRequestRepository.save(request);
    }

    @Transactional
    public DepositRequest cancel(UUID publicId, Long userId) {
        DepositRequest request = getForUser(publicId, userId);
        if (!USER_CANCELLABLE_STATUSES.contains(request.getStatus())) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "INVALID_STATUS",
                "Cannot cancel deposit request in status " + request.getStatus()
            );
        }
        request.cancel();
        return depositRequestRepository.save(request);
    }

    @Transactional
    public DepositRequest issueDetails(UUID publicId, String paymentDetails) {
        DepositRequest request = getByPublicId(publicId);
        requireStatus(request, DepositRequestStatus.PENDING_DETAILS, "issue payment details");
        if (paymentDetails == null || paymentDetails.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PAYMENT_DETAILS_REQUIRED",
                "Payment details are required"
            );
        }
        request.startWaitingPayment(paymentDetails);
        return depositRequestRepository.save(request);
    }

    @Transactional
    public DepositRequest confirm(UUID publicId) {
        DepositRequest request = getByPublicIdForUpdate(publicId);
        if (request.getStatus() == DepositRequestStatus.CONFIRMED) {
            return request;
        }
        requireStatus(request, DepositRequestStatus.PAYMENT_VERIFICATION, "confirm payment");

        UserAccount account = getUserAccountForDeposit(request);
        UserAccountTx tx = new UserAccountTx(
            account,
            request.getAmount(),
            TX_TYPE_DEPOSIT,
            REF_TYPE_DEPOSIT_REQUEST,
            request.getId()
        );
        userAccountTxRepository.save(tx);
        request.confirm();
        return depositRequestRepository.save(request);
    }

    @Transactional
    public DepositRequest reject(UUID publicId, String rejectReason) {
        DepositRequest request = getByPublicId(publicId);
        requireStatus(request, DepositRequestStatus.PAYMENT_VERIFICATION, "reject payment");
        request.reject(rejectReason);
        return depositRequestRepository.save(request);
    }

    public DepositRequest getByPublicIdForAdmin(UUID publicId) {
        return getByPublicId(publicId);
    }

    private DepositRequest getByPublicId(UUID publicId) {
        return depositRequestRepository.findByPublicId(publicId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "DEPOSIT_REQUEST_NOT_FOUND",
                "Deposit request not found"
            ));
    }

    private DepositRequest getByPublicIdForUpdate(UUID publicId) {
        return depositRequestRepository.findByPublicIdForUpdate(publicId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "DEPOSIT_REQUEST_NOT_FOUND",
                "Deposit request not found"
            ));
    }

    private UserAccount getUserAccountForDeposit(DepositRequest request) {
        String currencyCode = request.getDepositMethod().getCurrencyCode();
        return userAccountService.getOrCreateAccount(request.getUserId(), currencyCode);
    }

    private void requireStatus(DepositRequest request, DepositRequestStatus status, String action) {
        if (request.getStatus() != status) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "INVALID_STATUS",
                "Cannot " + action + " in status " + request.getStatus()
            );
        }
    }

    private DepositRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DepositRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_STATUS",
                "Unknown status " + status
            );
        }
    }

    private Set<DepositRequestStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }

        Set<DepositRequestStatus> parsed = EnumSet.noneOf(DepositRequestStatus.class);
        for (String raw : statuses) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String token : raw.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    parsed.add(DepositRequestStatus.valueOf(trimmed.toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    throw new ApiProblemException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_STATUS",
                        "Unknown status " + trimmed
                    );
                }
            }
        }
        return parsed;
    }
}
