package com.example.currencyconverter.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExchangeRateResponse {

    private String fromCurrency;
    private String toCurrency;
    private double exchangeRate;
}