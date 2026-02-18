package ru.maltsev.primemarketbackend.auth.verification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    @Query("select evt from EmailVerificationToken evt join fetch evt.user where evt.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    void deleteByUserId(Long userId);
}
