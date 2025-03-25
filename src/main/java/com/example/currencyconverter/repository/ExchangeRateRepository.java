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

    @Query("SELECT er FROM ExchangeRate er "
            + "WHERE (er.fromCurrencyCode = :fromCurrencyCode AND er.toCurrencyCode = :toCurrencyCode) "
            + "ORDER BY er.rate ASC")
    List<ExchangeRate> findMinRate(@Param("fromCurrencyCode") String fromCurrencyCode, @Param("toCurrencyCode") String toCurrencyCode);
}