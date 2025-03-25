package com.example.currencyconverter.service;

import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.repository.BankRepository;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository;
import com.example.currencyconverter.utils.InMemoryCache;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;
    private final InMemoryCache<String, Object> exchangeRateCache;

    @Value("${cache.enabled:true}") // Default to true if not set
    private boolean cacheEnabled;

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    private String getCacheKey(Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        return bankId + "-" + fromCurrencyCode + "-" + toCurrencyCode;
    }

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
                .build();

        ExchangeRate savedExchangeRate = exchangeRateRepository.save(exchangeRate);

        if (cacheEnabled) {
            invalidateCache(bankId, fromCurrency.getCode(), toCurrency.getCode());
        }

        return savedExchangeRate;
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
                    ExchangeRate updatedRate = exchangeRateRepository.save(exchangeRate);

                    if (cacheEnabled) {
                        invalidateCache(exchangeRate.getBank().getId(), fromCurrencyCode, toCurrencyCode);
                    }


                    return updatedRate;
                })
                .orElse(null);
    }

    public boolean deleteExchangeRate(Long id) {
        if (exchangeRateRepository.existsById(id)) {
            ExchangeRate exchangeRate = exchangeRateRepository.findById(id).get(); // Get the entity before deleting
            exchangeRateRepository.deleteById(id);

            if (cacheEnabled) {
                invalidateCache(exchangeRate.getBank().getId(), exchangeRate.getFromCurrencyCode(), exchangeRate.getToCurrencyCode());
            }

            return true;
        }
        return false;
    }

    public BigDecimal getExchangeRateValue(
            Long bankId, String fromCurrencyCode, String toCurrencyCode) {

        String cacheKey = getCacheKey(bankId, fromCurrencyCode, toCurrencyCode);

        // Check cache first
        if (cacheEnabled && exchangeRateCache.get(cacheKey) != null) {

            return (BigDecimal) exchangeRateCache.get(cacheKey);
        }


        Optional<ExchangeRate> exchangeRate = exchangeRateRepository
                .findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                        bankId, fromCurrencyCode, toCurrencyCode);

        if (exchangeRate.isEmpty()) {
            return null;
        }

        BigDecimal rate = exchangeRate.get().getRate();

        // Put in cache
        if (cacheEnabled) {
            exchangeRateCache.put(cacheKey, rate);

        }

        return rate;
    }

    // Helper method to invalidate cache
    private void invalidateCache(Long bankId, String fromCurrencyCode, String toCurrencyCode) {

    }

    public ExchangeRate getMinRate(String fromCurrencyCode, String toCurrencyCode) {
        List<ExchangeRate> exchangeRate = exchangeRateRepository.findMinRate(fromCurrencyCode, toCurrencyCode);
        if (exchangeRate.isEmpty()) {
            return null;
        }
        return exchangeRate.get(0);
    }
}