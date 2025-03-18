package com.example.currencyconverter.repository;

import com.example.currencyconverter.entity.ExchangeRate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
            Long bankId, String fromCurrencyCode, String toCurrencyCode);

    List<ExchangeRate> findByBankId(Long bankId);
}