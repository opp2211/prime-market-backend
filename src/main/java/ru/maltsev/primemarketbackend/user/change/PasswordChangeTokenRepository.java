package ru.maltsev.primemarketbackend.user.change;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordChangeTokenRepository extends JpaRepository<PasswordChangeToken, Long> {
    @Query("select pct from PasswordChangeToken pct join fetch pct.user where pct.tokenHash = :tokenHash")
    Optional<PasswordChangeToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    void deleteByUserId(Long userId);
}
