package com.example.currencyconverter.controller;

import com.example.currencyconverter.model.ConversionRequest;
import com.example.currencyconverter.model.ConversionResponse;
import com.example.currencyconverter.model.ExchangeRateResponse;
import com.example.currencyconverter.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping("/convert")
    public ResponseEntity<ConversionResponse> convertCurrency(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam double amount) {

        ConversionRequest request = new ConversionRequest(from, to, amount);
        ConversionResponse response = currencyService.convertCurrency(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/exchangeRate/{from}/{to}")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @PathVariable String from,
            @PathVariable String to) {

        ExchangeRateResponse response = currencyService.getExchangeRate(from, to);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(Exception.class) // Обработчик всех исключений
    public ResponseEntity<String> handleExceptions(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Invalid input: " + e.getMessage());
    }
}