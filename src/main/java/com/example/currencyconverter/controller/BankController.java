package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.BankDto;
import com.example.currencyconverter.dto.ErrorResponseDto;
import com.example.currencyconverter.dto.ExchangeRateDto;
import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.service.BankService;
import com.example.currencyconverter.service.ExchangeRateService;
import com.example.currencyconverter.utils.InMemoryCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/banks")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bank Management", description = "Endpoints for managing banks and their exchange rates.")
@CrossOrigin(origins = "https://currency-converter-ui-wccs.onrender.com") // Разрешаем запросы с http://localhost:3000
public class BankController {

    private final BankService bankService;
    private final ExchangeRateService exchangeRateService;
    private final InMemoryCache<String, Object> controllerCache;

    @PostMapping
    @Operation(summary = "Create a new bank", description = "Creates a new bank record. The name must be unique.")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Bank created successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankDto.class))), @ApiResponse(responseCode = "400", description = "Invalid input (e.g., blank name, name too short/long, or name already exists)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<BankDto> createBank(
            @Parameter(description = "Name of the new bank (must be unique)", required = true, example = "Central Bank")
            @RequestParam @NotBlank(message = "Bank name cannot be blank") @Size(min = 2, max = 100) String name) {
        Bank newBank = bankService.createBank(name);
        controllerCache.clear();

        return new ResponseEntity<>(convertToDto(newBank), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bank by ID", description = "Retrieves details of a specific bank, including its exchange rates.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bank found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied (e.g., not positive)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bank not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<BankDto> getBank(
            @Parameter(description = "ID of the bank to retrieve", required = true, example = "1")
            @PathVariable @Positive(message = "Bank ID must be positive") Long id) {
        String cacheKey = "/banks/" + id;
        ResponseEntity<BankDto> cachedResponse = (ResponseEntity<BankDto>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {

            return cachedResponse;
        }

        Bank bank = bankService.getBank(id).orElseThrow(() -> new CurrencyNotFoundException("Bank not found with id: " + id));
        BankDto bankDto = convertToDto(bank);
        ResponseEntity<BankDto> response = new ResponseEntity<>(bankDto, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @GetMapping
    @Operation(summary = "Get all banks", description = "Retrieves a list of all banks with their exchange rates.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = BankDto.class))))
    })
    public ResponseEntity<List<BankDto>> getAllBanks() {
        String cacheKey = "/banks";
        @SuppressWarnings("unchecked")
        ResponseEntity<List<BankDto>> cachedResponse = (ResponseEntity<List<BankDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Bank> banks = bankService.getAllBanks();
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        ResponseEntity<List<BankDto>> response = new ResponseEntity<>(bankDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);

        return response;
    }

    @GetMapping("/by-currency")
    @Operation(summary = "Find banks by currency code", description = "Finds banks that have exchange rates involving the specified currency code. Optionally filters by a specific rate to BYN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Banks found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = BankDto.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid currency code or rate format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<BankDto>> getBanksByCurrency(
            @Parameter(description = "3-letter currency code (e.g., USD, EUR)", required = true, example = "USD")
            @RequestParam @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @Parameter(description = "Optional: Exact exchange rate to BYN for the specified currency code", required = false, example = "3.2500")
            @RequestParam(required = false) @Positive @Digits(integer = 15, fraction = 4) BigDecimal rateToBYN) {
        String cacheKey = "/banks/by-currency?currencyCode=" + currencyCode + "&rateToBYN=" + rateToBYN;
        ResponseEntity<List<BankDto>> cachedResponse = (ResponseEntity<List<BankDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Bank> banks = bankService.findBanksByCurrencyAndRateToBYN(currencyCode, rateToBYN);
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        ResponseEntity<List<BankDto>> response = new ResponseEntity<>(bankDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @GetMapping("/search")
    @Operation(summary = "Search banks by name", description = "Searches for banks whose name contains the specified query string (case-insensitive).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = BankDto.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid search query (e.g., blank)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<BankDto>> searchBanksByName(
            @Parameter(description = "Part of the bank name to search for", required = true, example = "Bank")
            @RequestParam @NotBlank @Size(min = 1, max = 100) String name) {
        String cacheKey = "/banks/search?name=" + name;
        ResponseEntity<List<BankDto>> cachedResponse = (ResponseEntity<List<BankDto>>) controllerCache.get(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Bank> banks = bankService.findBanksByNameLikeNative(name);
        List<BankDto> bankDtos = banks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        ResponseEntity<List<BankDto>> response = new ResponseEntity<>(bankDtos, HttpStatus.OK);
        controllerCache.put(cacheKey, response);
        return response;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update bank name", description = "Updates the name of an existing bank. The new name must be unique.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bank updated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID or new name supplied (validation error or name already exists)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bank not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<BankDto> updateBank(
            @Parameter(description = "ID of the bank to update", required = true, example = "1")
            @PathVariable @Positive Long id,
            @Parameter(description = "New unique name for the bank", required = true, example = "National Bank")
            @RequestParam @NotBlank @Size(min = 2, max = 100) String newName) {
        Bank updatedBank = bankService.updateBank(id, newName);
        controllerCache.clear();
        return new ResponseEntity<>(convertToDto(updatedBank), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a bank", description = "Deletes a bank and all its associated exchange rates.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Bank deleted successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bank not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteBank(
            @Parameter(description = "ID of the bank to delete", required = true, example = "1")
            @PathVariable @Positive Long id) {
        bankService.deleteBank(id);
        controllerCache.clear();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private BankDto convertToDto(Bank bank) {
        BankDto bankDto = new BankDto();
        bankDto.setId(bank.getId());
        bankDto.setName(bank.getName());
        List<ExchangeRate> exchangeRates = exchangeRateService.getAllExchangeRatesByBankId(bank.getId());
        List<ExchangeRateDto> exchangeRateDtos = exchangeRates.stream()
                .map(this::convertToExchangeRateDto)
                .collect(Collectors.toList());
        bankDto.setExchangeRates(exchangeRateDtos);
        return bankDto;
    }

    private ExchangeRateDto convertToExchangeRateDto(ExchangeRate exchangeRate) {
        ExchangeRateDto exchangeRateDto = new ExchangeRateDto();
        exchangeRateDto.setId(exchangeRate.getId());
        exchangeRateDto.setRate(exchangeRate.getRate());
        exchangeRateDto.setFromCurrencyCode(exchangeRate.getFromCurrencyCode());
        exchangeRateDto.setToCurrencyCode(exchangeRate.getToCurrencyCode());
        exchangeRateDto.setBankId(exchangeRate.getBank().getId());
        return exchangeRateDto;
    }
}