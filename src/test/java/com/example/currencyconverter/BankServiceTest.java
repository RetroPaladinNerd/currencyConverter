package com.example.currencyconverter;

import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.repository.BankRepository;
import com.example.currencyconverter.service.BankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock
    private BankRepository bankRepository;

    @InjectMocks
    private BankService bankService;

    private Bank bank1;
    private Bank bank2;

    @BeforeEach
    void setUp() {
        bank1 = Bank.builder().id(1L).name("Bank One").build();
        bank2 = Bank.builder().id(2L).name("Bank Two").build();
    }

    @Test
    @DisplayName("createBank should save and return bank")
    void createBank_Success() {
        String bankName = "New Bank";
        // Mock save to return the saved entity with an ID
        when(bankRepository.save(any(Bank.class))).thenAnswer(invocation -> {
            Bank b = invocation.getArgument(0);
            b.setId(3L); // Simulate ID generation
            return b;
        });

        Bank result = bankService.createBank(bankName);

        assertNotNull(result);
        assertEquals(3L, result.getId());
        assertEquals(bankName, result.getName());

        ArgumentCaptor<Bank> bankCaptor = ArgumentCaptor.forClass(Bank.class);
        verify(bankRepository).save(bankCaptor.capture());
        assertEquals(bankName, bankCaptor.getValue().getName());
        //assertNull(bankCaptor.getValue().getId()); // ID should be null before save
    }

    @Test
    @DisplayName("getBank should return Optional of bank when found")
    void getBank_Found() {
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank1));

        Optional<Bank> result = bankService.getBank(1L);

        assertTrue(result.isPresent());
        assertEquals(bank1.getName(), result.get().getName());
        verify(bankRepository).findById(1L);
    }

    @Test
    @DisplayName("getBank should return empty Optional when not found")
    void getBank_NotFound() {
        when(bankRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Bank> result = bankService.getBank(99L);

        assertFalse(result.isPresent());
        verify(bankRepository).findById(99L);
    }

    @Test
    @DisplayName("getAllBanks should return list of banks")
    void getAllBanks_Success() {
        List<Bank> banks = Arrays.asList(bank1, bank2);
        when(bankRepository.findAll()).thenReturn(banks);

        List<Bank> result = bankService.getAllBanks();

        assertEquals(2, result.size());
        assertSame(banks, result);
        verify(bankRepository).findAll();
    }

    @Test
    @DisplayName("updateBank should update and return bank when found")
    void updateBank_Found() {
        String newName = "Updated Bank One";
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank1));
        when(bankRepository.save(any(Bank.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Return the modified bank

        Bank result = bankService.updateBank(1L, newName);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(newName, result.getName());

        ArgumentCaptor<Bank> bankCaptor = ArgumentCaptor.forClass(Bank.class);
        verify(bankRepository).save(bankCaptor.capture());
        assertEquals(newName, bankCaptor.getValue().getName()); // Verify name was updated before save
        verify(bankRepository).findById(1L);
    }

    @Test
    @DisplayName("updateBank should return null when not found")
    void updateBank_NotFound() {
        String newName = "Updated Bank";
        when(bankRepository.findById(99L)).thenReturn(Optional.empty());

        Bank result = bankService.updateBank(99L, newName);

        assertNull(result);
        verify(bankRepository).findById(99L);
        verify(bankRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteBank should return true and call deleteById when found")
    void deleteBank_Found() {
        when(bankRepository.existsById(1L)).thenReturn(true);
        doNothing().when(bankRepository).deleteById(1L); // Mock void method

        boolean result = bankService.deleteBank(1L);

        assertTrue(result);
        verify(bankRepository).existsById(1L);
        verify(bankRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteBank should return false and not call deleteById when not found")
    void deleteBank_NotFound() {
        when(bankRepository.existsById(99L)).thenReturn(false);

        boolean result = bankService.deleteBank(99L);

        assertFalse(result);
        verify(bankRepository).existsById(99L);
        verify(bankRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("findBanksByExchangeRateCurrencyCode should call repository method")
    void findBanksByExchangeRateCurrencyCode_CallsRepository() {
        String currencyCode = "USD";
        List<Bank> expectedBanks = Collections.singletonList(bank1);
        when(bankRepository.findBanksByExchangeRateCurrencyCode(currencyCode)).thenReturn(expectedBanks);

        List<Bank> result = bankService.findBanksByExchangeRateCurrencyCode(currencyCode);

        assertSame(expectedBanks, result);
        verify(bankRepository).findBanksByExchangeRateCurrencyCode(currencyCode);
    }

    @Test
    @DisplayName("findBanksByCurrencyAndRateToBYN should call specific repo method when rate is provided")
    void findBanksByCurrencyAndRateToBYN_WithRate() {
        String currencyCode = "USD";
        BigDecimal rate = new BigDecimal("3.2000");
        List<Bank> expectedBanks = Collections.singletonList(bank1);
        when(bankRepository.findBanksByCurrencyAndRateToBYN(currencyCode, "BYN", rate)).thenReturn(expectedBanks);

        List<Bank> result = bankService.findBanksByCurrencyAndRateToBYN(currencyCode, rate);

        assertSame(expectedBanks, result);
        verify(bankRepository).findBanksByCurrencyAndRateToBYN(currencyCode, "BYN", rate);
        verify(bankRepository, never()).findBanksByExchangeRateCurrencyCode(anyString());
    }

    @Test
    @DisplayName("findBanksByCurrencyAndRateToBYN should call general repo method when rate is null")
    void findBanksByCurrencyAndRateToBYN_WithoutRate() {
        String currencyCode = "EUR";
        List<Bank> expectedBanks = Arrays.asList(bank1, bank2);
        when(bankRepository.findBanksByExchangeRateCurrencyCode(currencyCode)).thenReturn(expectedBanks);

        List<Bank> result = bankService.findBanksByCurrencyAndRateToBYN(currencyCode, null);

        assertSame(expectedBanks, result);
        verify(bankRepository).findBanksByExchangeRateCurrencyCode(currencyCode);
        verify(bankRepository, never()).findBanksByCurrencyAndRateToBYN(anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("findBanksByNameLikeNative should call repository method")
    void findBanksByNameLikeNative_CallsRepository() {
        String nameQuery = "One";
        List<Bank> expectedBanks = Collections.singletonList(bank1);
        when(bankRepository.findBanksByNameLikeNative(nameQuery)).thenReturn(expectedBanks);

        List<Bank> result = bankService.findBanksByNameLikeNative(nameQuery);

        assertSame(expectedBanks, result);
        verify(bankRepository).findBanksByNameLikeNative(nameQuery);
    }
}