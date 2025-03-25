package com.example.currencyconverter.repository;

import com.example.currencyconverter.entity.Bank;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankRepository extends JpaRepository<Bank, Long> {

    @Query("SELECT b FROM Bank b JOIN b.exchangeRates er WHERE er.fromCurrencyCode = :currencyCode OR er.toCurrencyCode = :currencyCode")
    List<Bank> findBanksByExchangeRateCurrencyCode(@Param("currencyCode") String currencyCode);

    @Query("SELECT b FROM Bank b JOIN b.exchangeRates er "
            + "WHERE (er.fromCurrencyCode = :currencyCode AND er.toCurrencyCode = :targetCurrency AND er.rate = :rate) "
            + "OR (er.toCurrencyCode = :currencyCode AND er.fromCurrencyCode = :targetCurrency AND er.rate = :rate)")
    List<Bank> findBanksByCurrencyAndRateToBYN(
            @Param("currencyCode") String currencyCode,
            @Param("targetCurrency") String targetCurrency,
            @Param("rate") BigDecimal rate);

    @Query(value = "SELECT * FROM banks WHERE LOWER(name) LIKE LOWER(concat('%', :name, '%'))", nativeQuery = true)
    List<Bank> findBanksByNameLikeNative(@Param("name") String name);
}