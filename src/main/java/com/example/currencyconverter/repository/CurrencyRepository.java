package com.example.currencyconverter.repository;

import com.example.currencyconverter.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    Currency findByCode(String code);
}