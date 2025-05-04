package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.ConversionResponseDto;
import com.example.currencyconverter.dto.ErrorResponseDto;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.model.ConversionRequest;
import com.example.currencyconverter.service.CurrencyService;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
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
@RequestMapping("/currencies")
@RequiredArgsConstructor
@Validated
@Tag(name = "Currency Operations", description = "Endpoints for managing currencies and performing conversions")
@CrossOrigin(origins = "http://localhost:3000") // Разрешаем запросы с http://localhost:3000
public class CurrencyController {

    private final CurrencyService currencyService;
    private final InMemoryCache<String, Object> controllerCache;

    @PostMapping
    @Operation(summary = "Create a new currency", description = "Creates a new currency. The 3-letter code must be unique.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Currency created successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Currency.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input (validation error or code already exists)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Currency> createCurrency(
            @Parameter(description = "Unique 3-letter currency code (ISO 4217)", required = true, example = "JPY")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String code,
            @Parameter(description = "Full name of the currency", required = true, example = "Japanese Yen")
            @RequestParam @NotBlank @Size(min = 2, max = 100) String name) {
        Currency newCurrency = currencyService.createCurrency(code, name);
        controllerCache.clear();
        return new ResponseEntity<>(newCurrency, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get currency by ID", description = "Retrieves details of a specific currency by its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Currency found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Currency.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Currency not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Currency> getCurrency(
            @Parameter(description = "ID of the currency to retrieve", required = true, example = "3")
            @PathVariable @Positive Long id) {
        String cacheKey = "/currencies/" + id;
        @SuppressWarnings("unchecked")
        ResponseEntity<Currency> cachedResponse = (ResponseEntity<Currency>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        Currency currency = currencyService.getCurrency(id).orElseThrow(() -> new CurrencyNotFoundException("Currency not found with id: " + id));
        ResponseEntity<Currency> response = new ResponseEntity<>(currency, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @GetMapping
    @Operation(summary = "Get all currencies", description = "Retrieves a list of all available currencies.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = Currency.class))))
    })
    public ResponseEntity<List<Currency>> getAllCurrencies() {
        String cacheKey = "/currencies";
        @SuppressWarnings("unchecked")
        ResponseEntity<List<Currency>> cachedResponse = (ResponseEntity<List<Currency>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Currency> currencies = currencyService.getAllCurrencies();
        ResponseEntity<List<Currency>> response = new ResponseEntity<>(currencies, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update currency", description = "Updates the code and/or name of an existing currency. The new code must be unique.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Currency updated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Currency.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input (validation error or new code already exists)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Currency not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Currency> updateCurrency(
            @Parameter(description = "ID of the currency to update", required = true, example = "3")
            @PathVariable @Positive Long id,
            @Parameter(description = "New unique 3-letter currency code", required = true, example = "JPY")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String newCode,
            @Parameter(description = "New full name for the currency", required = true, example = "Japanese Yen")
            @RequestParam @NotBlank @Size(min = 2, max = 100) String newName) {
        Currency updatedCurrency = currencyService.updateCurrency(id, newCode, newName);
        controllerCache.clear();
        return new ResponseEntity<>(updatedCurrency, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete currency", description = "Deletes a currency by its ID. Fails if the currency is used in existing exchange rates.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Currency deleted successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Currency not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Currency is in use and cannot be deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteCurrency(
            @Parameter(description = "ID of the currency to delete", required = true, example = "3")
            @PathVariable @Positive Long id) {
        currencyService.deleteCurrency(id);
        controllerCache.clear();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/convert")
    @Operation(summary = "Convert currency",
            description = "Converts an amount from one currency to another using the exchange rate of a specific bank.")
    @RequestBody(description = "Details for the currency conversion: bank ID, from/to currency codes, and amount.", required = true,
            content = @Content(schema = @Schema(implementation = ConversionRequest.class)))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conversion successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ConversionResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input (validation error in request body, or required exchange rate not found)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDto.class))),
    })
    public ResponseEntity<ConversionResponseDto> convertCurrency(
            @Valid @org.springframework.web.bind.annotation.RequestBody ConversionRequest request) {
        ConversionResponseDto response = currencyService.convertCurrency(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}