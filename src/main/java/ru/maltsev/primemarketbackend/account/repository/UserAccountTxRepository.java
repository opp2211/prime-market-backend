package ru.maltsev.primemarketbackend.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;

public interface UserAccountTxRepository extends JpaRepository<UserAccountTx, Long> {
}
