package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.service.CurrencyService;
import com.example.currencyconverter.service.ExchangeRateService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
        return new ResponseEntity<>(convertToDto(newExchangeRate), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExchangeRateDto> getExchangeRate(@PathVariable Long id) {
        return exchangeRateService.getExchangeRate(id)
                .map(this::convertToDto)
                .map(exchangeRateDto -> new ResponseEntity<>(exchangeRateDto, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<List<ExchangeRateDto>> getAllExchangeRates() {
        List<ExchangeRate> exchangeRates = exchangeRateService.getAllExchangeRates();
        List<ExchangeRateDto> exchangeRateDtos = exchangeRates.stream()
                .map(this::convertToDto)
                .toList();
        return new ResponseEntity<>(exchangeRateDtos, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExchangeRateDto> updateExchangeRate(
            @PathVariable Long id,
            @RequestParam String fromCurrencyCode,
            @RequestParam String toCurrencyCode,
            @RequestParam BigDecimal newRate) {
        ExchangeRate updatedExchangeRate = exchangeRateService.updateExchangeRate(
                        id, fromCurrencyCode, toCurrencyCode, newRate);
        if (updatedExchangeRate != null) {
            return new ResponseEntity<>(convertToDto(updatedExchangeRate), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExchangeRate(@PathVariable Long id) {
        boolean deleted = exchangeRateService.deleteExchangeRate(id);
        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
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