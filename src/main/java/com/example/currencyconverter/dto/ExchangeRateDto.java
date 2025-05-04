package com.example.currencyconverter.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class ExchangeRateDto {
    private Long id;
    private BigDecimal rate;
    private String fromCurrencyCode;
    private String toCurrencyCode;
    private Long bankId; // <-- Добавьте это поле
}