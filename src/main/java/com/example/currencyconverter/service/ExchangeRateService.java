package com.example.currencyconverter.service;

import com.example.currencyconverter.dto.ExchangeRateCreateRequestDto;
import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.exception.InvalidInputDataException;
import com.example.currencyconverter.repository.BankRepository;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository;
import com.example.currencyconverter.utils.InMemoryCache;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;
    private final InMemoryCache<String, Object> exchangeRateCache;

    @Value("${cache.enabled:true}")
    private boolean cacheEnabled;

    private String getCacheKey(Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        return bankId + "-" + fromCurrencyCode + "-" + toCurrencyCode;
    }

    @Transactional
    public ExchangeRate createExchangeRateWithCodes(
            Long bankId, String fromCurrencyCode, String toCurrencyCode, BigDecimal rate) {
        log.debug("Attempting to create exchange rate for bankId: {}, from: {}, to: {}, rate: {}",
                bankId, fromCurrencyCode, toCurrencyCode, rate);

        final Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> {
                    log.warn("Bank not found with id: {}", bankId);
                    return new CurrencyNotFoundException("Bank not found with id: " + bankId);
                });

        validateCurrencyCode(fromCurrencyCode, "from");
        validateCurrencyCode(toCurrencyCode, "to");
        validateDuplicateRate(bankId, fromCurrencyCode, toCurrencyCode);

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .bank(bank)
                .fromCurrencyCode(fromCurrencyCode)
                .toCurrencyCode(toCurrencyCode)
                .rate(rate)
                .build();

        ExchangeRate savedRate = exchangeRateRepository.save(exchangeRate);
        log.info("Successfully created exchange rate with id: {}", savedRate.getId());
        clearCacheForRate(bankId, fromCurrencyCode, toCurrencyCode);
        return savedRate;
    }


    /**
     * Creates multiple exchange rates in a single transaction using Stream API.
     *
     * @param requests List of DTOs containing data for new exchange rates.
     * @return List of created ExchangeRate entities.
     * @throws InvalidInputDataException if any validation fails (bank/currency not found, duplicate rate).
     *                                   The transaction will be rolled back.
     */
    @Transactional
    public List<ExchangeRate> createExchangeRatesBulk(List<ExchangeRateCreateRequestDto> requests) {
        log.info("Attempting to create {} exchange rates in bulk.", requests.size());
        if (requests == null || requests.isEmpty()) {
            log.warn("Bulk create request list is null or empty.");
            return new ArrayList<>();
        }
        Set<Long> bankIds = requests.stream()
                .map(ExchangeRateCreateRequestDto::getBankId)
                .collect(Collectors.toSet());
        Set<String> currencyCodes = requests.stream()
                .flatMap(r -> Stream.of(r.getFromCurrencyCode(), r.getToCurrencyCode()))
                .collect(Collectors.toSet());
        Map<Long, Bank> banksMap = bankRepository.findAllById(bankIds).stream()
                .collect(Collectors.toMap(Bank::getId, Function.identity()));
        Map<String, Currency> currenciesMap = currencyRepository.findAll().stream()
                .filter(c -> currencyCodes.contains(c.getCode()))
                .collect(Collectors.toMap(Currency::getCode, Function.identity()));


        List<ExchangeRate> entitiesToSave = requests.stream()
                .map(request -> {
                    Bank bank = banksMap.get(request.getBankId());
                    if (bank == null) {
                        log.error("Bank not found for ID: {} in bulk request.", request.getBankId());
                        throw new CurrencyNotFoundException("Bank not found with id: " + request.getBankId());
                    }
                    if (!currenciesMap.containsKey(request.getFromCurrencyCode())) {
                        log.error("FromCurrency not found for code: {} in bulk request.", request.getFromCurrencyCode());
                        throw new InvalidInputDataException("Invalid 'from' currency code: " + request.getFromCurrencyCode());
                    }
                    if (!currenciesMap.containsKey(request.getToCurrencyCode())) {
                        log.error("ToCurrency not found for code: {} in bulk request.", request.getToCurrencyCode());
                        throw new InvalidInputDataException("Invalid 'to' currency code: " + request.getToCurrencyCode());
                    }
                    validateDuplicateRate(request.getBankId(), request.getFromCurrencyCode(), request.getToCurrencyCode());
                    return ExchangeRate.builder()
                            .bank(bank)
                            .fromCurrencyCode(request.getFromCurrencyCode())
                            .toCurrencyCode(request.getToCurrencyCode())
                            .rate(request.getRate())
                            .build();
                })
                .collect(Collectors.toList());
        List<ExchangeRate> savedEntities = exchangeRateRepository.saveAll(entitiesToSave);
        log.info("Successfully saved {} exchange rates.", savedEntities.size());
        savedEntities.forEach(rate -> clearCacheForRate(rate.getBank().getId(), rate.getFromCurrencyCode(), rate.getToCurrencyCode()));
        log.debug("Cache cleared for {} newly created exchange rates.", savedEntities.size());

        return savedEntities;
    }
    @Transactional
    public ExchangeRate updateExchangeRate(
            Long id, String fromCurrencyCode, String toCurrencyCode, BigDecimal newRate) {
        log.debug("Attempting to update exchange rate with id: {}, from: {}, to: {}, newRate: {}",
                id, fromCurrencyCode, toCurrencyCode, newRate);

        ExchangeRate exchangeRate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("ExchangeRate not found with id: {}", id);
                    return new CurrencyNotFoundException("Exchange Rate not found with id: " + id);
                });
        validateCurrencyCode(fromCurrencyCode, "from");
        validateCurrencyCode(toCurrencyCode, "to");
        if (!exchangeRate.getFromCurrencyCode().equals(fromCurrencyCode) || !exchangeRate.getToCurrencyCode().equals(toCurrencyCode)) {
            exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    exchangeRate.getBank().getId(), fromCurrencyCode, toCurrencyCode
            ).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    log.warn("Update would create a duplicate exchange rate for bankId: {}, from: {}, to: {}",
                            exchangeRate.getBank().getId(), fromCurrencyCode, toCurrencyCode);
                    throw new InvalidInputDataException(String.format(
                            "Another exchange rate from %s to %s already exists for this bank.", fromCurrencyCode, toCurrencyCode
                    ));
                }
            });
        }
        clearCacheForRate(exchangeRate.getBank().getId(), exchangeRate.getFromCurrencyCode(), exchangeRate.getToCurrencyCode());

        exchangeRate.setFromCurrencyCode(fromCurrencyCode);
        exchangeRate.setToCurrencyCode(toCurrencyCode);
        exchangeRate.setRate(newRate);

        ExchangeRate updatedRate = exchangeRateRepository.save(exchangeRate);
        log.info("Successfully updated exchange rate with id: {}", updatedRate.getId());
        clearCacheForRate(updatedRate.getBank().getId(), updatedRate.getFromCurrencyCode(), updatedRate.getToCurrencyCode());
        return updatedRate;
    }

    public List<ExchangeRate> getAllExchangeRates() {
        log.debug("Fetching all exchange rates");
        List<ExchangeRate> rates = exchangeRateRepository.findAll();
        return rates;
    }

    public List<ExchangeRate> getAllExchangeRatesByBankId(Long bankId) {
        log.debug("Fetching all exchange rates for bankId: {}", bankId);
        return exchangeRateRepository.findByBankId(bankId);
    }


    public Optional<ExchangeRate> getExchangeRate(Long id) {
        log.debug("Fetching exchange rate by id: {}", id);
        return exchangeRateRepository.findById(id);
    }

    public BigDecimal getExchangeRateValue(
            Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        log.trace("Attempting to get exchange rate value for bankId: {}, from: {}, to: {}", bankId, fromCurrencyCode, toCurrencyCode);
        String cacheKey = getCacheKey(bankId, fromCurrencyCode, toCurrencyCode);

        if (cacheEnabled) {
            Object cachedValue = exchangeRateCache.get(cacheKey);
            if (cachedValue instanceof BigDecimal) {
                log.trace("Cache hit for key: {}", cacheKey);
                return (BigDecimal) cachedValue;
            } else if (cachedValue != null) {
                log.warn("Invalid cache entry type found for key: {}. Expected BigDecimal, got {}. Evicting.", cacheKey, cachedValue.getClass().getName());
                exchangeRateCache.evict(cacheKey);
            }
            log.trace("Cache miss for key: {}", cacheKey);
        }

        Optional<ExchangeRate> exchangeRateOpt = exchangeRateRepository
                .findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                        bankId, fromCurrencyCode, toCurrencyCode);

        if (exchangeRateOpt.isEmpty()) {
            log.warn("Exchange rate not found in DB for bankId: {}, from: {}, to: {}", bankId, fromCurrencyCode, toCurrencyCode);
            return null;
        }

        BigDecimal rate = exchangeRateOpt.get().getRate();

        if (cacheEnabled) {
            log.trace("Putting value into cache for key: {}", cacheKey);
            exchangeRateCache.put(cacheKey, rate);
        }

        return rate;
    }

    public ExchangeRate getMinRate(String fromCurrencyCode, String toCurrencyCode) {
        log.debug("Finding minimum exchange rate from {} to {}", fromCurrencyCode, toCurrencyCode);
        validateCurrencyCode(fromCurrencyCode, "from");
        validateCurrencyCode(toCurrencyCode, "to");


        List<ExchangeRate> rates = exchangeRateRepository.findMinRate(fromCurrencyCode, toCurrencyCode);
        if (rates.isEmpty()) {
            log.info("No exchange rates found from {} to {}", fromCurrencyCode, toCurrencyCode);
            return null;
        }
        log.debug("Minimum rate found with id: {}", rates.get(0).getId());
        return rates.get(0);
    }

    @Transactional
    public boolean deleteExchangeRate(Long id) {
        log.debug("Attempting to delete exchange rate with id: {}", id);
        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isPresent()) {
            ExchangeRate rate = rateOpt.get();
            clearCacheForRate(rate.getBank().getId(), rate.getFromCurrencyCode(), rate.getToCurrencyCode());
            exchangeRateRepository.deleteById(id);
            log.info("Successfully deleted exchange rate with id: {}", id);
            return true;
        } else {
            log.warn("Exchange rate with id: {} not found for deletion.", id);
            return false;
        }
    }
    private void clearCacheForRate(Long bankId, String fromCode, String toCode) {
        if (cacheEnabled) {
            String cacheKey = getCacheKey(bankId, fromCode, toCode);
            log.trace("Clearing cache for key: {}", cacheKey);
            exchangeRateCache.evict(cacheKey);
        }
    }
    private void validateCurrencyCode(String currencyCode, String type) {
        if (currencyRepository.findByCode(currencyCode) == null) {
            log.warn("{}Currency not found with code: {}", type.substring(0, 1).toUpperCase() + type.substring(1), currencyCode);
            throw new InvalidInputDataException(String.format("Invalid '%s' currency code: %s", type, currencyCode));
        }
    }
    private void validateDuplicateRate(Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        Optional<ExchangeRate> existingRate = exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                bankId, fromCurrencyCode, toCurrencyCode
        );
        if (existingRate.isPresent()) {
            log.warn("Duplicate exchange rate detected for bankId: {}, from: {}, to: {}", bankId, fromCurrencyCode, toCurrencyCode);
            throw new InvalidInputDataException(String.format(
                    "Exchange rate from %s to %s already exists for this bank.", fromCurrencyCode, toCurrencyCode
            ));
        }
    }
}