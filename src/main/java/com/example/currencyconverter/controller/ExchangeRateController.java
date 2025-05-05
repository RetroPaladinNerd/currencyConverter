package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.ErrorResponseDto;
import com.example.currencyconverter.dto.ExchangeRateCreateRequestDto;
import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.service.ExchangeRateService;
import com.example.currencyconverter.utils.InMemoryCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin; // Импорт аннотации CORS


@RestController
@RequestMapping("/exchange-rates")
@RequiredArgsConstructor
@Validated
@Tag(name = "Exchange Rate Management", description = "Endpoints for managing exchange rates between currencies for specific banks.")
@CrossOrigin(origins = "https://currency-converter-ui-wccs.onrender.com") // Разрешаем запросы с http://localhost:3000
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final InMemoryCache<String, Object> controllerCache;

    @PostMapping
    @Operation(summary = "Create an exchange rate", description = "Creates a new exchange rate for a specific bank between two currencies. The combination of bank, from_currency, and to_currency must be unique.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Exchange rate created successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ExchangeRateDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input (validation error, non-existent bank/currency, or duplicate rate exists)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bank or Currency not found (caught by service)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ExchangeRateDto> createExchangeRate(
            @Parameter(description = "ID of the bank for this rate", required = true, example = "1")
            @RequestParam @NotNull @Positive Long bankId,
            @Parameter(description = "3-letter code of the source currency", required = true, example = "USD")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String fromCurrencyCode,
            @Parameter(description = "3-letter code of the target currency", required = true, example = "EUR")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String toCurrencyCode,
            @Parameter(description = "The exchange rate (how many target units for one source unit)", required = true, example = "0.9250")
            @RequestParam @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal rate) {
        ExchangeRate newExchangeRate = exchangeRateService.createExchangeRateWithCodes(bankId, fromCurrencyCode, toCurrencyCode, rate);
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(newExchangeRate), HttpStatus.CREATED);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Create multiple exchange rates", description = "Creates multiple exchange rates in a single request. Uses Stream API for processing. If any rate fails validation (e.g., bad bank ID, non-existent currency, duplicate), the entire operation is rolled back (transactional).")
    @RequestBody(description = "A list of exchange rates to create.", required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ExchangeRateCreateRequestDto.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Exchange rates created successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = ExchangeRateDto.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid input in the request body (validation error on list or individual items, non-existent bank/currency, or duplicate rate detected)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bank or Currency not found during processing (caught by service, results in 400 for the batch)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<ExchangeRateDto>> createExchangeRatesBulk(
            @org.springframework.web.bind.annotation.RequestBody
            List<@Valid ExchangeRateCreateRequestDto> requests
    ) {
        List<ExchangeRate> createdRates = exchangeRateService.createExchangeRatesBulk(requests);
        List<ExchangeRateDto> createdRateDtos = createdRates.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        controllerCache.clear();
        return new ResponseEntity<>(createdRateDtos, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get exchange rate by ID", description = "Retrieves details of a specific exchange rate by its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exchange rate found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ExchangeRateDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exchange rate not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ExchangeRateDto> getExchangeRate(
            @Parameter(description = "ID of the exchange rate to retrieve", required = true, example = "10")
            @PathVariable @Positive Long id) {
        String cacheKey = "/exchange-rates/" + id;
        @SuppressWarnings("unchecked")
        ResponseEntity<ExchangeRateDto> cachedResponse = (ResponseEntity<ExchangeRateDto>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        ExchangeRate exchangeRate = exchangeRateService.getExchangeRate(id).orElseThrow(() -> new CurrencyNotFoundException("Exchange Rate not found with id: " + id));
        ExchangeRateDto dto = convertToDto(exchangeRate);
        ResponseEntity<ExchangeRateDto> response = new ResponseEntity<>(dto, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @GetMapping
    @Operation(summary = "Get all exchange rates", description = "Retrieves a list of all exchange rates across all banks.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = ExchangeRateDto.class))))
    })
    public ResponseEntity<List<ExchangeRateDto>> getAllExchangeRates() {
        String cacheKey = "/exchange-rates";
        @SuppressWarnings("unchecked")
        ResponseEntity<List<ExchangeRateDto>> cachedResponse = (ResponseEntity<List<ExchangeRateDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<ExchangeRate> exchangeRates = exchangeRateService.getAllExchangeRates();
        List<ExchangeRateDto> exchangeRateDtos = exchangeRates.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        ResponseEntity<List<ExchangeRateDto>> response = new ResponseEntity<>(exchangeRateDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an exchange rate", description = "Updates the currency codes and/or rate for an existing exchange rate.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exchange rate updated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ExchangeRateDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input (validation error, non-existent currency, potential duplicate)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exchange rate not found or Currency not found (caught by service)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ExchangeRateDto> updateExchangeRate(
            @Parameter(description = "ID of the exchange rate to update", required = true, example = "10")
            @PathVariable @Positive Long id,
            @Parameter(description = "New 3-letter code for the source currency", required = true, example = "USD")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String fromCurrencyCode,
            @Parameter(description = "New 3-letter code for the target currency", required = true, example = "CAD")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String toCurrencyCode,
            @Parameter(description = "New exchange rate", required = true, example = "1.3550")
            @RequestParam @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal newRate) {
        ExchangeRate updatedExchangeRate = exchangeRateService.updateExchangeRate(id, fromCurrencyCode, toCurrencyCode, newRate);
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(updatedExchangeRate), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an exchange rate", description = "Deletes a specific exchange rate by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Exchange rate deleted successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exchange rate not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteExchangeRate(
            @Parameter(description = "ID of the exchange rate to delete", required = true, example = "10")
            @PathVariable @Positive Long id) {
        boolean deleted = exchangeRateService.deleteExchangeRate(id);
        if (!deleted) {
            throw new CurrencyNotFoundException("Exchange rate not found with id: " + id);
        }
        controllerCache.clear();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/min-rate")
    @Operation(summary = "Get minimum exchange rate", description = "Finds the bank offering the minimum exchange rate for a given currency pair.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Minimum rate found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ExchangeRateDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid currency code supplied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No exchange rate found for this currency pair or invalid currency code (caught by service)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ExchangeRateDto> getMinRate(
            @Parameter(description = "3-letter code of the source currency", required = true, example = "GBP")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String fromCurrencyCode,
            @Parameter(description = "3-letter code of the target currency", required = true, example = "USD")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String toCurrencyCode) {
        String cacheKey = "/exchange-rates/min-rate?fromCurrencyCode=" + fromCurrencyCode + "&toCurrencyCode=" + toCurrencyCode;
        @SuppressWarnings("unchecked")
        ResponseEntity<ExchangeRateDto> cachedResponse = (ResponseEntity<ExchangeRateDto>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        ExchangeRate exchangeRate = exchangeRateService.getMinRate(fromCurrencyCode, toCurrencyCode);
        if (exchangeRate == null) {
            throw new CurrencyNotFoundException(String.format("No exchange rates found for conversion from %s to %s", fromCurrencyCode, toCurrencyCode));
        }
        ExchangeRateDto dto = convertToDto(exchangeRate);
        ResponseEntity<ExchangeRateDto> response = new ResponseEntity<>(dto, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }
    private ExchangeRateDto convertToDto(ExchangeRate exchangeRate) {
        if (exchangeRate == null) {
            return null;
        }
        ExchangeRateDto dto = new ExchangeRateDto();
        dto.setId(exchangeRate.getId());
        dto.setRate(exchangeRate.getRate());
        dto.setFromCurrencyCode(exchangeRate.getFromCurrencyCode());
        dto.setToCurrencyCode(exchangeRate.getToCurrencyCode());
        dto.setBankId(exchangeRate.getBank().getId());
        return dto;
    }
}