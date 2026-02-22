package ru.maltsev.primemarketbackend.account.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    List<UserAccount> findAllByUserId(Long userId);
}