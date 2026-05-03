package ru.maltsev.primemarketbackend.platform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransaction;

public interface PlatformAccountTransactionRepository extends JpaRepository<PlatformAccountTransaction, Long> {
    @EntityGraph(attributePaths = "platformAccount")
    Page<PlatformAccountTransaction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
