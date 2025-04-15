package com.example.currencyconverter.repository;

import com.example.currencyconverter.entity.ExchangeRate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
            Long bankId, String fromCurrencyCode, String toCurrencyCode);

    List<ExchangeRate> findByBankId(Long bankId);

    @Query(value = "SELECT * FROM exchange_rates "
            + "WHERE from_currency_code = :fromCurrencyCode AND to_currency_code = :toCurrencyCode "
            + "ORDER BY rate ASC LIMIT 1", nativeQuery = true)
    List<ExchangeRate> findMinRate(@Param("fromCurrencyCode") String fromCurrencyCode, @Param("toCurrencyCode") String toCurrencyCode);

}