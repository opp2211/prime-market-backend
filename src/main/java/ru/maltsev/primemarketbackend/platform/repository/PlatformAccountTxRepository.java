package ru.maltsev.primemarketbackend.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTx;

public interface PlatformAccountTxRepository extends JpaRepository<PlatformAccountTx, Long> {
}
