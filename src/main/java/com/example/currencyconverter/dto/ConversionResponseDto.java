package com.example.currencyconverter.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ConversionResponseDto {
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amount;
    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
}