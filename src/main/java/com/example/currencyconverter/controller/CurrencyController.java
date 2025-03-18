package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.ConversionResponseDto;
import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.model.ConversionRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;

    @PostMapping
    public ResponseEntity<Currency> createCurrency(@RequestParam String code,
                                                   @RequestParam String name) {
        Currency newCurrency = currencyService.createCurrency(code, name);
        return new ResponseEntity<>(newCurrency, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Currency> getCurrency(@PathVariable Long id) {
        return currencyService.getCurrency(id)
                .map(currency -> new ResponseEntity<>(currency, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<List<Currency>> getAllCurrencies() {
        List<Currency> currencies = currencyService.getAllCurrencies();
        return new ResponseEntity<>(currencies, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Currency> updateCurrency(@PathVariable Long id,
                                                   @RequestParam String newCode,
                                                   @RequestParam String newName) {
        Currency updatedCurrency = currencyService.updateCurrency(id, newCode, newName);
        if (updatedCurrency != null) {
            return new ResponseEntity<>(updatedCurrency, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCurrency(@PathVariable Long id) {
        boolean deleted = currencyService.deleteCurrency(id);
        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


    @PostMapping("/convert")
    public ResponseEntity<ConversionResponseDto> convertCurrency(
            @RequestParam(required = false) Long bankId,
            @RequestParam(required = false) String fromCurrencyCode,
            @RequestParam(required = false) String toCurrencyCode,
            @RequestParam(required = false) BigDecimal amount,
            @RequestBody(required = false) ConversionRequest request
    ) {
        try {
            ConversionRequest conversionRequest =
                    request != null ? request : new ConversionRequest();
            if (request == null) {
                conversionRequest.setBankId(bankId);
                conversionRequest.setFromCurrencyCode(fromCurrencyCode);
                conversionRequest.setToCurrencyCode(toCurrencyCode);
                conversionRequest.setAmount(amount);
            }
            ConversionResponseDto response = currencyService.convertCurrency(conversionRequest);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (UnsupportedOperationException e) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED); // или другой код ошибки
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/exchangeRate")
    public ResponseEntity<ExchangeRateDto> createExchangeRate(
            @RequestParam Long bankId,
            @RequestParam String fromCurrencyCode,
            @RequestParam String toCurrencyCode,
            @RequestParam BigDecimal rate
    ) {
        Currency fromCurrency = currencyService.getCurrencyByCode(fromCurrencyCode);
        if (fromCurrency == null) {
            return  new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Currency toCurrency = currencyService.getCurrencyByCode(toCurrencyCode);
        if (toCurrency == null) {
            return  new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        ExchangeRate newExchangeRate =
                exchangeRateService.createExchangeRate(bankId, fromCurrency, toCurrency, rate);
        return new ResponseEntity<>(convertToDto(newExchangeRate), HttpStatus.CREATED);
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