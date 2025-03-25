package com.example.currencyconverter.controller;


import com.example.currencyconverter.config.CacheConfig;
import com.example.currencyconverter.dto.BankDto;
import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.service.BankService;
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
@RequestMapping("/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final ExchangeRateService exchangeRateService;
    private final CacheConfig cacheConfig;
    @Qualifier("bankCache") // Correct Qualifier
    private final InMemoryCache<String, Object> controllerCache;

    @PostMapping
    public ResponseEntity<BankDto> createBank(@RequestParam String name) {
        Bank newBank = bankService.createBank(name);
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(newBank), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankDto> getBank(@PathVariable Long id) {
        String cacheKey = "/banks/" + id;
        ResponseEntity<BankDto> cachedResponse = (ResponseEntity<BankDto>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        return bankService.getBank(id)
                .map(this::convertToDto)
                .map(bankDto -> {
                    ResponseEntity<BankDto> response = new ResponseEntity<>(bankDto, HttpStatus.OK);
                    controllerCache.put(cacheKey, response);
                    return response;
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<List<BankDto>> getAllBanks() {
        String cacheKey = "/banks";
        ResponseEntity<List<BankDto>> cachedResponse = (ResponseEntity<List<BankDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        List<Bank> banks = bankService.getAllBanks();
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .toList(); // Changed from collect(Collectors.toList())
        ResponseEntity<List<BankDto>> response = new ResponseEntity<>(bankDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @GetMapping("/by-currency")
    public ResponseEntity<List<BankDto>> getBanksByCurrency(
            @RequestParam String currencyCode,
            @RequestParam(required = false) BigDecimal rateToBYN) {
        String cacheKey = "/banks/by-currency?currencyCode=" + currencyCode + "&rateToBYN=" + rateToBYN;
        ResponseEntity<List<BankDto>> cachedResponse = (ResponseEntity<List<BankDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Bank> banks = bankService.findBanksByCurrencyAndRateToBYN(currencyCode, rateToBYN);
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .toList(); // Changed from collect(Collectors.toList())

        ResponseEntity<List<BankDto>> response = new ResponseEntity<>(bankDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @GetMapping("/search")
    public ResponseEntity<List<BankDto>> searchBanksByName(@RequestParam String name) {
        String cacheKey = "/banks/search?name=" + name;
        ResponseEntity<List<BankDto>> cachedResponse = (ResponseEntity<List<BankDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Bank> banks = bankService.findBanksByNameLikeNative(name);
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .toList(); // Changed from collect(Collectors.toList())

        ResponseEntity<List<BankDto>> response = new ResponseEntity<>(bankDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankDto> updateBank(@PathVariable Long id, @RequestParam String newName) {
        Bank updatedBank = bankService.updateBank(id, newName);
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(updatedBank), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBank(@PathVariable Long id) {
        boolean deleted = bankService.deleteBank(id);
        controllerCache.clear();
        if (deleted) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    private BankDto convertToDto(Bank bank) {
        BankDto bankDto = new BankDto();
        bankDto.setId(bank.getId());
        bankDto.setName(bank.getName());
        List<ExchangeRate> exchangeRates = exchangeRateService.getAllExchangeRates();
        List<ExchangeRateDto> exchangeRateDtos = exchangeRates.stream()
                .filter(exchangeRate -> exchangeRate.getBank().getId().equals(bank.getId()))
                .map(this::convertToExchangeRateDto)
                .toList(); // Changed from collect(Collectors.toList())
        bankDto.setExchangeRates(exchangeRateDtos);
        return bankDto;
    }

    private ExchangeRateDto convertToExchangeRateDto(ExchangeRate exchangeRate) {
        ExchangeRateDto exchangeRateDto = new ExchangeRateDto();
        exchangeRateDto.setId(exchangeRate.getId());
        exchangeRateDto.setRate(exchangeRate.getRate());
        exchangeRateDto.setFromCurrencyCode(exchangeRate.getFromCurrencyCode());
        exchangeRateDto.setToCurrencyCode(exchangeRate.getToCurrencyCode());
        return exchangeRateDto;
    }
}