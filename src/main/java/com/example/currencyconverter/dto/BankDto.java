package com.example.currencyconverter.dto;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class BankDto {
    private Long id;
    private String name;
    private List<ExchangeRateDto> exchangeRates;
}