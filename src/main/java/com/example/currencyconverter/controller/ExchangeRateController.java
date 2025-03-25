package com.example.currencyconverter.controller;

import com.example.currencyconverter.config.CacheConfig;
import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.service.CurrencyService;
import com.example.currencyconverter.service.ExchangeRateService;
import com.example.currencyconverter.utils.InMemoryCache;
import java.math.BigDecimal;
import java.util.List;
// Removed unused import: java.util.stream.Collectors; // No longer needed
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final CurrencyService currencyService;
    private final CacheConfig cacheConfig;
    @Qualifier("exchangeRateCache") // Correct Qualifier
    private final InMemoryCache<String, Object> controllerCache;

    @PostMapping
    public ResponseEntity<ExchangeRateDto> createExchangeRate(
            @RequestParam Long bankId,
            @RequestParam String fromCurrencyCode,
            @RequestParam String toCurrencyCode,
            @RequestParam BigDecimal rate
    ) {
        Currency fromCurrency = currencyService.getCurrencyByCode(fromCurrencyCode);
        if (fromCurrency == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Currency toCurrency = currencyService.getCurrencyByCode(toCurrencyCode);
        if (toCurrency == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        ExchangeRate newExchangeRate =
                exchangeRateService.createExchangeRate(bankId, fromCurrency, toCurrency, rate);
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(newExchangeRate), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExchangeRateDto> getExchangeRate(@PathVariable Long id) {
        String cacheKey = "/exchange-rates/" + id;
        ResponseEntity<ExchangeRateDto> cachedResponse = (ResponseEntity<ExchangeRateDto>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        return exchangeRateService.getExchangeRate(id)
                .map(this::convertToDto)
                .map(exchangeRateDto -> {
                    ResponseEntity<ExchangeRateDto> response = new ResponseEntity<>(exchangeRateDto, HttpStatus.OK);
                    controllerCache.put(cacheKey, response);
                    return response;
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<List<ExchangeRateDto>> getAllExchangeRates() {
        String cacheKey = "/exchange-rates";
        ResponseEntity<List<ExchangeRateDto>> cachedResponse = (ResponseEntity<List<ExchangeRateDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<ExchangeRate> exchangeRates = exchangeRateService.getAllExchangeRates();
        List<ExchangeRateDto> exchangeRateDtos = exchangeRates.stream()
                .map(this::convertToDto)
                .toList(); // Changed from collect(Collectors.toList())
        ResponseEntity<List<ExchangeRateDto>> response = new ResponseEntity<>(exchangeRateDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExchangeRateDto> updateExchangeRate(
            @PathVariable Long id,
            @RequestParam String fromCurrencyCode,
            @RequestParam String toCurrencyCode, // Added missing parameter
            @RequestParam BigDecimal newRate) {
        ExchangeRate updatedExchangeRate = exchangeRateService.updateExchangeRate(
                id, fromCurrencyCode, toCurrencyCode, newRate); // Passed toCurrencyCode
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(updatedExchangeRate), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExchangeRate(@PathVariable Long id) {
        boolean deleted = exchangeRateService.deleteExchangeRate(id);
        controllerCache.clear();
        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/min-rate")
    public ResponseEntity<ExchangeRateDto> getMinRate(
            @RequestParam String fromCurrencyCode,
            @RequestParam String toCurrencyCode) {
        String cacheKey = "/exchange-rates/min-rate?fromCurrencyCode=" + fromCurrencyCode + "&toCurrencyCode=" + toCurrencyCode;
        ResponseEntity<ExchangeRateDto> cachedResponse = (ResponseEntity<ExchangeRateDto>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        ExchangeRate exchangeRate = exchangeRateService.getMinRate(fromCurrencyCode, toCurrencyCode);
        if (exchangeRate != null) {
            ExchangeRateDto dto = convertToDto(exchangeRate);
            ResponseEntity<ExchangeRateDto> response = new ResponseEntity<>(dto, HttpStatus.OK);
            controllerCache.put(cacheKey, response);
            return response;
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/cache/size")
    public ResponseEntity<Double> getCacheSizeInKB() {
        double cacheSizeKB = cacheConfig.getCacheSizeInKB();
        return new ResponseEntity<>(cacheSizeKB, HttpStatus.OK);
    }

    private ExchangeRateDto convertToDto(ExchangeRate exchangeRate) {
        ExchangeRateDto dto = new ExchangeRateDto();
        dto.setId(exchangeRate.getId());
        dto.setRate(exchangeRate.getRate());
        dto.setFromCurrencyCode(exchangeRate.getFromCurrencyCode());
        dto.setToCurrencyCode(exchangeRate.getToCurrencyCode());
        return dto;
    }
}