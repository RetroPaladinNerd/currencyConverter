package com.example.currencyconverter.service;

import com.example.currencyconverter.dto.ConversionResponseDto;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.model.ConversionRequest;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateRepository exchangeRateRepository;

    public ConversionResponseDto convertCurrency(ConversionRequest request) {
        BigDecimal exchangeRateValue = exchangeRateService.getExchangeRateValue(
                request.getBankId(),
                request.getFromCurrencyCode(),
                request.getToCurrencyCode()
        );

        if (exchangeRateValue == null) {
            throw new IllegalArgumentException(
                    "Exchange rate not found for this bank and currency pair.");
        }

        BigDecimal amount = request.getAmount();
        BigDecimal convertedAmount = amount.multiply(exchangeRateValue);

        ConversionResponseDto response = new ConversionResponseDto();
        response.setFromCurrency(request.getFromCurrencyCode());
        response.setToCurrency(request.getToCurrencyCode());
        response.setAmount(amount);
        response.setConvertedAmount(convertedAmount);
        response.setExchangeRate(exchangeRateValue);

        return response;
    }

    public Currency getCurrencyByCode(String code) {
        return currencyRepository.findByCode(code);
    }

    public Currency createCurrency(String code, String name) {
        Currency currency = new Currency();
        currency.setCode(code);
        currency.setName(name);
        return currencyRepository.save(currency);
    }

    public Optional<Currency> getCurrency(Long id) {
        return currencyRepository.findById(id);
    }

    public List<Currency> getAllCurrencies() {
        return currencyRepository.findAll();
    }

    public Currency updateCurrency(Long id, String newCode, String newName) {
        return currencyRepository.findById(id)
                .map(currency -> {
                    currency.setCode(newCode);
                    currency.setName(newName);
                    return currencyRepository.save(currency);
                })
                .orElse(null);
    }

    public boolean deleteCurrency(Long id) {
        if (currencyRepository.existsById(id)) {
            currencyRepository.deleteById(id);
            return true;
        }
        return false;
    }
}