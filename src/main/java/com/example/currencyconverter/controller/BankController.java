package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.BankDto;
import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.service.BankService;
import com.example.currencyconverter.service.ExchangeRateService;
import java.util.List;
import java.util.stream.Collectors;
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
@RequestMapping("/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final ExchangeRateService exchangeRateService;

    @PostMapping
    public ResponseEntity<BankDto> createBank(@RequestParam String name) {
        Bank newBank = bankService.createBank(name);
        return new ResponseEntity<>(convertToDto(newBank), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankDto> getBank(@PathVariable Long id) {
        return bankService.getBank(id)
                .map(this::convertToDto)
                .map(bankDto -> new ResponseEntity<>(bankDto, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<List<BankDto>> getAllBanks() {
        List<Bank> banks = bankService.getAllBanks();
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new ResponseEntity<>(bankDtos, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankDto> updateBank(@PathVariable Long id, @RequestParam String newName) {
        Bank updatedBank = bankService.updateBank(id, newName);
        if (updatedBank != null) {
            return new ResponseEntity<>(convertToDto(updatedBank), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBank(@PathVariable Long id) {
        boolean deleted = bankService.deleteBank(id);
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
                .collect(Collectors.toList());
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