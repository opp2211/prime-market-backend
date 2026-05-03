package ru.maltsev.primemarketbackend.currency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.currency.domain.UserCurrencyConversion;

public interface UserCurrencyConversionRepository extends JpaRepository<UserCurrencyConversion, Long> {
}
