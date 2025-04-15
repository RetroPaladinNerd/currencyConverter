// src/test/java/com/example/currencyconverter/service/CurrencyServiceTest.java
package com.example.currencyconverter;

import com.example.currencyconverter.dto.ConversionResponseDto;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.model.ConversionRequest;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository; // Нужен для delete
import com.example.currencyconverter.service.CurrencyService;
import com.example.currencyconverter.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private ExchangeRateService exchangeRateService; // Mocked dependency
    @Mock
    private ExchangeRateRepository exchangeRateRepository; // Mocked for delete check (if implemented)

    @InjectMocks
    private CurrencyService currencyService;

    private Currency usd;
    private Currency eur;

    @BeforeEach
    void setUp() {
        usd = Currency.builder().id(1L).code("USD").name("US Dollar").build();
        eur = Currency.builder().id(2L).code("EUR").name("Euro").build();
    }

    @Nested
    @DisplayName("convertCurrency Tests")
    class ConvertCurrencyTests {

        @Test
        @DisplayName("Should convert currency successfully when rate exists")
        void convertCurrency_Success() {
            ConversionRequest request = new ConversionRequest();
            request.setBankId(10L);
            request.setFromCurrencyCode("USD");
            request.setToCurrencyCode("EUR");
            request.setAmount(new BigDecimal("100.00"));

            BigDecimal rate = new BigDecimal("0.95");
            when(exchangeRateService.getExchangeRateValue(10L, "USD", "EUR")).thenReturn(rate);

            ConversionResponseDto response = currencyService.convertCurrency(request);

            assertNotNull(response);
            assertEquals("USD", response.getFromCurrency());
            assertEquals("EUR", response.getToCurrency());
            assertEquals(new BigDecimal("100.00"), response.getAmount());
            assertEquals(rate, response.getExchangeRate());
            assertEquals(0, new BigDecimal("95.00").compareTo(response.getConvertedAmount()));

            verify(exchangeRateService).getExchangeRateValue(10L, "USD", "EUR");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when rate does not exist")
        void convertCurrency_RateNotFound() {
            ConversionRequest request = new ConversionRequest();
            request.setBankId(10L);
            request.setFromCurrencyCode("USD");
            request.setToCurrencyCode("JPY");
            request.setAmount(new BigDecimal("100.00"));

            when(exchangeRateService.getExchangeRateValue(10L, "USD", "JPY")).thenReturn(null); // Rate not found

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    currencyService.convertCurrency(request)
            );

            assertEquals("Exchange rate not found for this bank and currency pair.", exception.getMessage());
            verify(exchangeRateService).getExchangeRateValue(10L, "USD", "JPY");
        }
    }

    @Test
    @DisplayName("getCurrencyByCode should return currency when found")
    void getCurrencyByCode_Found() {
        when(currencyRepository.findByCode("USD")).thenReturn(usd);
        Currency result = currencyService.getCurrencyByCode("USD");
        assertSame(usd, result);
        verify(currencyRepository).findByCode("USD");
    }

    @Test
    @DisplayName("getCurrencyByCode should return null when not found")
    void getCurrencyByCode_NotFound() {
        when(currencyRepository.findByCode("JPY")).thenReturn(null);
        Currency result = currencyService.getCurrencyByCode("JPY");
        assertNull(result);
        verify(currencyRepository).findByCode("JPY");
    }

    @Test
    @DisplayName("createCurrency should save and return currency")
    void createCurrency_Success() {
        String code = "GBP";
        String name = "British Pound";
        when(currencyRepository.save(any(Currency.class))).thenAnswer(inv -> {
            Currency c = inv.getArgument(0);
            c.setId(3L);
            return c;
        });

        Currency result = currencyService.createCurrency(code, name);

        assertNotNull(result);
        assertEquals(3L, result.getId());
        assertEquals(code, result.getCode());
        assertEquals(name, result.getName());

        ArgumentCaptor<Currency> captor = ArgumentCaptor.forClass(Currency.class);
        verify(currencyRepository).save(captor.capture());
        assertEquals(code, captor.getValue().getCode());
        assertEquals(name, captor.getValue().getName());
        //assertNull(captor.getValue().getId());
    }

    @Test
    @DisplayName("getCurrency should return Optional of currency when found")
    void getCurrency_Found() {
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(usd));
        Optional<Currency> result = currencyService.getCurrency(1L);
        assertTrue(result.isPresent());
        assertSame(usd, result.get());
        verify(currencyRepository).findById(1L);
    }

    @Test
    @DisplayName("getCurrency should return empty Optional when not found")
    void getCurrency_NotFound() {
        when(currencyRepository.findById(99L)).thenReturn(Optional.empty());
        Optional<Currency> result = currencyService.getCurrency(99L);
        assertFalse(result.isPresent());
        verify(currencyRepository).findById(99L);
    }

    @Test
    @DisplayName("getAllCurrencies should return list of currencies")
    void getAllCurrencies_Success() {
        List<Currency> currencies = Arrays.asList(usd, eur);
        when(currencyRepository.findAll()).thenReturn(currencies);
        List<Currency> result = currencyService.getAllCurrencies();
        assertEquals(2, result.size());
        assertSame(currencies, result);
        verify(currencyRepository).findAll();
    }

    @Test
    @DisplayName("updateCurrency should update and return currency when found")
    void updateCurrency_Found() {
        String newCode = "USS";
        String newName = "United States Dollar Updated";
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(usd));
        when(currencyRepository.save(any(Currency.class))).thenAnswer(inv -> inv.getArgument(0));

        Currency result = currencyService.updateCurrency(1L, newCode, newName);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(newCode, result.getCode());
        assertEquals(newName, result.getName());

        ArgumentCaptor<Currency> captor = ArgumentCaptor.forClass(Currency.class);
        verify(currencyRepository).save(captor.capture());
        assertEquals(newCode, captor.getValue().getCode());
        assertEquals(newName, captor.getValue().getName());
        verify(currencyRepository).findById(1L);
    }

    @Test
    @DisplayName("updateCurrency should return null when not found")
    void updateCurrency_NotFound() {
        String newCode = "USS";
        String newName = "United States Dollar Updated";
        when(currencyRepository.findById(99L)).thenReturn(Optional.empty());

        Currency result = currencyService.updateCurrency(99L, newCode, newName);

        assertNull(result);
        verify(currencyRepository).findById(99L);
        verify(currencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteCurrency should return true and call deleteById when found")
    void deleteCurrency_Found() {
        when(currencyRepository.existsById(1L)).thenReturn(true);
        // Add check for related entities if implemented in service
        // when(exchangeRateRepository.countByCurrencyCode(usd.getCode())).thenReturn(0L); // Example check
        doNothing().when(currencyRepository).deleteById(1L);

        boolean result = currencyService.deleteCurrency(1L);

        assertTrue(result);
        verify(currencyRepository).existsById(1L);
        verify(currencyRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteCurrency should return false and not call deleteById when not found")
    void deleteCurrency_NotFound() {
        when(currencyRepository.existsById(99L)).thenReturn(false);

        boolean result = currencyService.deleteCurrency(99L);

        assertFalse(result);
        verify(currencyRepository).existsById(99L);
        verify(currencyRepository, never()).deleteById(anyLong());
    }

    // Optional: Add test for deleteCurrency when currency is in use (if that logic exists)
    /*
    @Test
    @DisplayName("deleteCurrency should throw exception when currency is in use")
    void deleteCurrency_InUse() {
        when(currencyRepository.existsById(1L)).thenReturn(true);
        when(exchangeRateRepository.countByCurrencyCode(usd.getCode())).thenReturn(5L); // Simulate it's used

        assertThrows(DataIntegrityViolationException.class, () -> // Or a custom exception
            currencyService.deleteCurrency(1L)
        );

        verify(currencyRepository).existsById(1L);
        verify(currencyRepository, never()).deleteById(anyLong());
    }
    */
}