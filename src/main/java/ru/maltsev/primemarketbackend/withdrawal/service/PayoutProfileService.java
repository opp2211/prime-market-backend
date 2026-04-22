package ru.maltsev.primemarketbackend.withdrawal.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.CreatePayoutProfileRequest;
import ru.maltsev.primemarketbackend.withdrawal.api.dto.UpdatePayoutProfileRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.PayoutProfile;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalMethod;
import ru.maltsev.primemarketbackend.withdrawal.repository.PayoutProfileRepository;
import ru.maltsev.primemarketbackend.withdrawal.repository.WithdrawalMethodRepository;

@Service
@RequiredArgsConstructor
public class PayoutProfileService {
    private final PayoutProfileRepository payoutProfileRepository;
    private final WithdrawalMethodRepository withdrawalMethodRepository;
    private final WithdrawalRequisitesValidator requisitesValidator;

    @Transactional(readOnly = true)
    public List<PayoutProfile> list(Long userId) {
        return payoutProfileRepository.findAllByUserIdAndActiveTrueOrderByDefaultProfileDescCreatedAtDesc(userId);
    }

    @Transactional
    public PayoutProfile create(Long userId, CreatePayoutProfileRequest request) {
        WithdrawalMethod method = loadActiveMethod(request.withdrawalMethodId());
        Map<String, Object> requisites = requisitesValidator.validateAndNormalize(method, request.requisites());
        return createForMethod(userId, method, request.title(), requisites, Boolean.TRUE.equals(request.isDefault()));
    }

    @Transactional
    public PayoutProfile update(Long userId, UUID publicId, UpdatePayoutProfileRequest request) {
        PayoutProfile profile = payoutProfileRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
            .orElseThrow(() -> notFound("PAYOUT_PROFILE_NOT_FOUND", "Payout profile not found"));
        if (!profile.isActive()) {
            throw notFound("PAYOUT_PROFILE_NOT_FOUND", "Payout profile not found");
        }

        String title = request.title() == null ? profile.getTitle() : normalizeTitle(request.title());
        Map<String, Object> requisites = request.requisites() == null
            ? profile.getRequisites()
            : requisitesValidator.validateAndNormalize(profile.getWithdrawalMethod(), request.requisites());
        if (request.title() == null && request.requisites() == null) {
            throw validationError("At least one field must be provided");
        }

        profile.update(title, requisites);
        return payoutProfileRepository.save(profile);
    }

    @Transactional
    public void delete(Long userId, UUID publicId) {
        PayoutProfile profile = payoutProfileRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
            .orElseThrow(() -> notFound("PAYOUT_PROFILE_NOT_FOUND", "Payout profile not found"));
        if (!profile.isActive()) {
            return;
        }
        profile.deactivate();
        payoutProfileRepository.save(profile);
    }

    @Transactional
    public PayoutProfile makeDefault(Long userId, UUID publicId) {
        PayoutProfile profile = payoutProfileRepository.findByPublicIdAndUserIdForUpdate(publicId, userId)
            .orElseThrow(() -> notFound("PAYOUT_PROFILE_NOT_FOUND", "Payout profile not found"));
        if (!profile.isActive()) {
            throw notFound("PAYOUT_PROFILE_NOT_FOUND", "Payout profile not found");
        }
        payoutProfileRepository.clearDefaultForUserAndMethod(
            userId,
            profile.getWithdrawalMethod().getId(),
            profile.getId()
        );
        profile.markDefault();
        return payoutProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public PayoutProfile getActiveForUser(UUID publicId, Long userId) {
        return payoutProfileRepository.findByPublicIdAndUserIdAndActiveTrue(publicId, userId)
            .orElseThrow(() -> notFound("PAYOUT_PROFILE_NOT_FOUND", "Payout profile not found"));
    }

    @Transactional
    public PayoutProfile createForMethod(
        Long userId,
        WithdrawalMethod method,
        String title,
        Map<String, Object> requisites,
        boolean requestedDefault
    ) {
        String normalizedTitle = normalizeTitle(title);
        boolean makeDefault = requestedDefault
            || !payoutProfileRepository.existsByUserIdAndWithdrawalMethodIdAndActiveTrue(userId, method.getId());
        if (makeDefault) {
            payoutProfileRepository.clearDefaultForUserAndMethod(userId, method.getId(), null);
        }

        PayoutProfile profile = new PayoutProfile(userId, method, normalizedTitle, requisites, makeDefault);
        return payoutProfileRepository.save(profile);
    }

    private WithdrawalMethod loadActiveMethod(Long withdrawalMethodId) {
        return withdrawalMethodRepository.findByIdAndActiveTrue(withdrawalMethodId)
            .orElseThrow(() -> notFound("WITHDRAWAL_METHOD_NOT_FOUND", "Withdrawal method not found"));
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw validationError("Field 'title' is required");
        }
        return title.trim();
    }

    private ApiProblemException validationError(String detail) {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            detail
        );
    }

    private ApiProblemException notFound(String code, String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, code, detail);
    }
}
