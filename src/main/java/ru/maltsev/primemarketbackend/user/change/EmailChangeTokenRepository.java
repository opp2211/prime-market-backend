package ru.maltsev.primemarketbackend.user.change;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, Long> {
    @Query("select ect from EmailChangeToken ect join fetch ect.user where ect.tokenHash = :tokenHash")
    Optional<EmailChangeToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    void deleteByUserId(Long userId);
}
