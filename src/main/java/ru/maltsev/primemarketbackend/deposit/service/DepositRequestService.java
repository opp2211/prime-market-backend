package ru.maltsev.primemarketbackend.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.maltsev.primemarketbackend.deposit.api.dto.AdminDepositRequestShortResponse;
import ru.maltsev.primemarketbackend.deposit.api.dto.CreateDepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositMethod;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;
import ru.maltsev.primemarketbackend.deposit.repository.DepositMethodRepository;
import ru.maltsev.primemarketbackend.deposit.repository.DepositRequestRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationActorType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEventType;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.money.service.MoneyOperationEventService;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    private final NotificationService notificationService;
    private final MoneyOperationEventService moneyOperationEventService;
    private final ObjectMapper objectMapper;

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
        DepositRequestStatus statusBeforeDetails = depositRequest.getStatus();
        if (paymentDetails != null && !paymentDetails.isBlank()) {
            depositRequest.startWaitingPayment(paymentDetails, null, null);
        } else {
            depositRequest.startPendingDetails();
        }

        DepositRequest savedRequest = depositRequestRepository.save(depositRequest);
        record(
            savedRequest,
            MoneyOperationEventType.DEPOSIT_CREATED,
            null,
            MoneyOperationActorType.USER,
            userId,
            null,
            null,
            moneyOperationEventService.payload(
                "amount", savedRequest.getAmount(),
                "currency_code", savedRequest.getCurrencyCodeSnapshot(),
                "deposit_method_id", method.getId(),
                "deposit_method_title", savedRequest.getDepositMethodTitleSnapshot()
            )
        );
        if (paymentDetails != null && !paymentDetails.isBlank()) {
            record(
                savedRequest,
                MoneyOperationEventType.DEPOSIT_DETAILS_ISSUED,
                statusBeforeDetails,
                MoneyOperationActorType.SYSTEM,
                null,
                null,
                null,
                moneyOperationEventService.payload("payment_details", paymentDetails)
            );
        }
        return savedRequest;
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

    public Page<AdminDepositRequestShortResponse> listShortForAdmin(List<String> statuses, Pageable pageable) {
        Set<DepositRequestStatus> parsedStatuses = parseStatuses(statuses);
        if (parsedStatuses == null || parsedStatuses.isEmpty()) {
            return depositRequestRepository.findAdminQueueRows(pageable)
                .map(AdminDepositRequestShortResponse::from);
        }
        return depositRequestRepository.findAdminQueueRowsByStatusIn(parsedStatuses, pageable)
            .map(AdminDepositRequestShortResponse::from);
    }

    @Transactional
    public DepositRequest markPaid(UUID publicId, Long userId) {
        DepositRequest request = depositRequestRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "DEPOSIT_REQUEST_NOT_FOUND",
                "Deposit request not found"
            ));
        if (request.getStatus() == DepositRequestStatus.PAYMENT_VERIFICATION) {
            return request;
        }
        requireStatus(request, DepositRequestStatus.WAITING_PAYMENT, "mark as paid");
        DepositRequestStatus statusBefore = request.getStatus();
        request.markPaid();
        DepositRequest savedRequest = depositRequestRepository.save(request);
        record(
            savedRequest,
            MoneyOperationEventType.DEPOSIT_USER_MARKED_PAID,
            statusBefore,
            MoneyOperationActorType.USER,
            userId,
            null,
            null,
            Map.of()
        );
        return savedRequest;
    }

    @Transactional
    public DepositRequest cancel(UUID publicId, Long userId) {
        DepositRequest request = depositRequestRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "DEPOSIT_REQUEST_NOT_FOUND",
                "Deposit request not found"
            ));
        if (request.getStatus() == DepositRequestStatus.CANCELLED) {
            return request;
        }
        if (!USER_CANCELLABLE_STATUSES.contains(request.getStatus())) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "INVALID_STATUS",
                "Cannot cancel deposit request in status " + request.getStatus()
            );
        }
        DepositRequestStatus statusBefore = request.getStatus();
        request.cancel();
        DepositRequest savedRequest = depositRequestRepository.save(request);
        record(
            savedRequest,
            MoneyOperationEventType.DEPOSIT_CANCELLED,
            statusBefore,
            MoneyOperationActorType.USER,
            userId,
            null,
            null,
            Map.of()
        );
        return savedRequest;
    }

    @Transactional
    public DepositRequest issueDetails(UUID publicId, Long actorUserId, String paymentDetails, String operatorComment) {
        DepositRequest request = getByPublicIdForUpdate(publicId);
        requireStatus(request, DepositRequestStatus.PENDING_DETAILS, "issue payment details");
        if (paymentDetails == null || paymentDetails.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PAYMENT_DETAILS_REQUIRED",
                "Payment details are required"
            );
        }
        String storedPaymentDetails = normalizePaymentDetailsForStorage(paymentDetails);
        DepositRequestStatus statusBefore = request.getStatus();
        request.startWaitingPayment(storedPaymentDetails, actorUserId, operatorComment);
        DepositRequest savedRequest = depositRequestRepository.save(request);
        record(
            savedRequest,
            MoneyOperationEventType.DEPOSIT_DETAILS_ISSUED,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            null,
            operatorComment,
            moneyOperationEventService.payload("payment_details", storedPaymentDetails)
        );
        return savedRequest;
    }

    @Transactional
    public DepositRequest confirm(UUID publicId, Long actorUserId, String confirmationReference, String operatorComment) {
        DepositRequest request = getByPublicIdForUpdate(publicId);
        if (request.getStatus() == DepositRequestStatus.CONFIRMED) {
            return request;
        }
        requireStatus(request, DepositRequestStatus.PAYMENT_VERIFICATION, "confirm payment");
        DepositRequestStatus statusBefore = request.getStatus();

        UserAccount account = getUserAccountForDeposit(request);
        UserAccountTx tx = new UserAccountTx(
            account,
            request.getAmount(),
            TX_TYPE_DEPOSIT,
            REF_TYPE_DEPOSIT_REQUEST,
            request.getId()
        );
        userAccountTxRepository.save(tx);
        request.confirm(actorUserId, confirmationReference, operatorComment);
        DepositRequest confirmedRequest = depositRequestRepository.save(request);
        record(
            confirmedRequest,
            MoneyOperationEventType.DEPOSIT_CONFIRMED,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            null,
            operatorComment,
            moneyOperationEventService.payload(
                "confirmation_reference", confirmationReference,
                "credited_amount", request.getAmount(),
                "currency_code", request.getCurrencyCodeSnapshot()
            )
        );
        notificationService.notifyDepositConfirmed(confirmedRequest);
        return confirmedRequest;
    }

    @Transactional
    public DepositRequest reject(UUID publicId, Long actorUserId, String rejectReason, String operatorComment) {
        DepositRequest request = getByPublicIdForUpdate(publicId);
        if (request.getStatus() == DepositRequestStatus.REJECTED) {
            return request;
        }
        requireStatus(request, DepositRequestStatus.PAYMENT_VERIFICATION, "reject payment");
        DepositRequestStatus statusBefore = request.getStatus();
        request.reject(actorUserId, rejectReason, operatorComment);
        DepositRequest rejectedRequest = depositRequestRepository.save(request);
        record(
            rejectedRequest,
            MoneyOperationEventType.DEPOSIT_REJECTED,
            statusBefore,
            MoneyOperationActorType.OPERATOR,
            actorUserId,
            rejectReason,
            operatorComment,
            Map.of()
        );
        notificationService.notifyDepositRejected(rejectedRequest);
        return rejectedRequest;
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

    private String normalizePaymentDetailsForStorage(String paymentDetails) {
        String trimmed = paymentDetails.trim();
        try {
            objectMapper.readTree(trimmed);
            return trimmed;
        } catch (JsonProcessingException ignored) {
            try {
                return objectMapper.writeValueAsString(Map.of("value", trimmed));
            } catch (JsonProcessingException ex) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_DETAILS_INVALID",
                    "Payment details cannot be encoded"
                );
            }
        }
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

    private void record(
        DepositRequest request,
        MoneyOperationEventType eventType,
        DepositRequestStatus statusBefore,
        MoneyOperationActorType actorType,
        Long actorUserId,
        String publicNote,
        String operatorNote,
        Map<String, Object> payload
    ) {
        moneyOperationEventService.record(
            MoneyOperationType.DEPOSIT_REQUEST,
            request.getId(),
            request.getPublicId(),
            eventType,
            statusBefore == null ? null : statusBefore.name(),
            request.getStatus() == null ? null : request.getStatus().name(),
            actorType,
            actorUserId,
            publicNote,
            operatorNote,
            payload
        );
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
