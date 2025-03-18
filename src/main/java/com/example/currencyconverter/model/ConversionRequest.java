package com.example.currencyconverter.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ConversionRequest {
    private Long bankId;
    private String fromCurrencyCode;
    private String toCurrencyCode;
    private BigDecimal amount;
}