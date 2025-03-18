package com.example.currencyconverter.service;

import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.repository.BankRepository;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;

    @Transactional
    public ExchangeRate createExchangeRate(
            Long bankId, Currency fromCurrency, Currency toCurrency, BigDecimal rate) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new IllegalArgumentException("Bank not found"));

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .bank(bank)
                .fromCurrencyCode(fromCurrency.getCode())
                .toCurrencyCode(toCurrency.getCode())
                .rate(rate)
                .currencies(Set.of(fromCurrency, toCurrency))
                .build();

        return exchangeRateRepository.save(exchangeRate);
    }

    public List<ExchangeRate> getAllExchangeRates() {
        return exchangeRateRepository.findAll();
    }

    public Optional<ExchangeRate> getExchangeRate(Long id) {
        return exchangeRateRepository.findById(id);
    }

    public ExchangeRate updateExchangeRate(
            Long id, String fromCurrencyCode, String toCurrencyCode, BigDecimal newRate) {
        return exchangeRateRepository.findById(id)
                .map(exchangeRate -> {
                    Currency fromCurrency = currencyRepository.findByCode(fromCurrencyCode);
                    if (fromCurrency == null) {
                        throw new IllegalArgumentException("From currency not found");
                    }
                    Currency toCurrency = currencyRepository.findByCode(toCurrencyCode);
                    if (toCurrency == null) {
                        throw new IllegalArgumentException("To currency not found");
                    }
                    exchangeRate.setFromCurrencyCode(fromCurrencyCode);
                    exchangeRate.setToCurrencyCode(toCurrencyCode);
                    exchangeRate.setRate(newRate);
                    return exchangeRateRepository.save(exchangeRate);
                })
                .orElse(null);
    }

    public boolean deleteExchangeRate(Long id) {
        if (exchangeRateRepository.existsById(id)) {
            exchangeRateRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public BigDecimal getExchangeRateValue(
            Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        Optional<ExchangeRate> exchangeRate = exchangeRateRepository
                .findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                        bankId, fromCurrencyCode, toCurrencyCode);
        if (exchangeRate.isEmpty()) {
            return null;
        }

        return exchangeRate.get().getRate();
    }
}