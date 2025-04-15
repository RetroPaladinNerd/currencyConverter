package com.example.currencyconverter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Schema(description = "Data Transfer Object for creating a single exchange rate within a bulk request")
public class ExchangeRateCreateRequestDto {

    @NotNull(message = "Bank ID cannot be null")
    @Positive(message = "Bank ID must be positive")
    @Schema(description = "ID of the bank for this rate", required = true, example = "1")
    private Long bankId;

    @NotBlank(message = "From currency code cannot be blank")
    @Size(min = 3, max = 3, message = "From currency code must be 3 characters long")
    @Schema(description = "3-letter code of the source currency", required = true, example = "USD")
    private String fromCurrencyCode;

    @NotBlank(message = "To currency code cannot be blank")
    @Size(min = 3, max = 3, message = "To currency code must be 3 characters long")
    @Schema(description = "3-letter code of the target currency", required = true, example = "EUR")
    private String toCurrencyCode;

    @NotNull(message = "Rate cannot be null")
    @Positive(message = "Rate must be positive")
    @Digits(integer = 15, fraction = 4, message = "Rate must have up to 15 integer digits and 4 fraction digits")
    @Schema(description = "The exchange rate (how many target units for one source unit)", required = true, example = "0.9250")
    private BigDecimal rate;
}