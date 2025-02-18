package com.example.currencyconverter.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversionResponse {

    private String fromCurrency;
    private String toCurrency;
    private double amount;
    private double convertedAmount;
}
