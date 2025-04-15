package com.example.currencyconverter;

import com.example.currencyconverter.dto.ExchangeRateCreateRequestDto;
import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.exception.InvalidInputDataException;
import com.example.currencyconverter.repository.BankRepository;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository;
import com.example.currencyconverter.service.ExchangeRateService;
import com.example.currencyconverter.utils.InMemoryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils; // Для установки cacheEnabled

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Mock
    private BankRepository bankRepository;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private InMemoryCache<String, Object> exchangeRateCache; // Мокаем кеш сервиса

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Captor
    private ArgumentCaptor<ExchangeRate> exchangeRateCaptor;
    @Captor
    private ArgumentCaptor<List<ExchangeRate>> exchangeRateListCaptor;
    @Captor
    private ArgumentCaptor<String> cacheKeyCaptor;
    @Captor
    private ArgumentCaptor<Object> cacheValueCaptor;


    private Bank testBank;
    private Currency usd;
    private Currency eur;
    private Currency gbp;
    private ExchangeRate testRateUsdEur;
    private ExchangeRateCreateRequestDto createDto;

    @BeforeEach
    void setUp() {
        // Включаем кеширование для большинства тестов, если не указано иное
        ReflectionTestUtils.setField(exchangeRateService, "cacheEnabled", true);

        testBank = Bank.builder().id(1L).name("Test Bank").build();
        usd = Currency.builder().id(10L).code("USD").name("US Dollar").build();
        eur = Currency.builder().id(11L).code("EUR").name("Euro").build();
        gbp = Currency.builder().id(12L).code("GBP").name("British Pound").build();

        testRateUsdEur = ExchangeRate.builder()
                .id(100L)
                .bank(testBank)
                .fromCurrencyCode("USD")
                .toCurrencyCode("EUR")
                .rate(new BigDecimal("0.9000"))
                .build();

        createDto = new ExchangeRateCreateRequestDto();
        createDto.setBankId(testBank.getId());
        createDto.setFromCurrencyCode(usd.getCode());
        createDto.setToCurrencyCode(eur.getCode());
        createDto.setRate(new BigDecimal("0.9500"));
    }

    // --- Тесты для createExchangeRateWithCodes ---
    @Nested
    @DisplayName("createExchangeRateWithCodes Tests")
    class CreateExchangeRateTests {

        @Test
        @DisplayName("Should create rate successfully when data is valid")
        void createExchangeRateWithCodes_Success() {
            when(bankRepository.findById(testBank.getId())).thenReturn(Optional.of(testBank));
            when(currencyRepository.findByCode(usd.getCode())).thenReturn(usd);
            when(currencyRepository.findByCode(eur.getCode())).thenReturn(eur);
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.empty()); // No duplicate
            when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> {
                ExchangeRate rateToSave = invocation.getArgument(0);
                rateToSave.setId(101L); // Simulate ID generation
                return rateToSave;
            });

            ExchangeRate result = exchangeRateService.createExchangeRateWithCodes(
                    testBank.getId(), usd.getCode(), eur.getCode(), createDto.getRate());

            assertNotNull(result);
            assertEquals(101L, result.getId());
            assertEquals(testBank, result.getBank());
            assertEquals(usd.getCode(), result.getFromCurrencyCode());
            assertEquals(eur.getCode(), result.getToCurrencyCode());
            assertEquals(createDto.getRate(), result.getRate());

            verify(exchangeRateRepository).save(exchangeRateCaptor.capture());
            ExchangeRate savedEntity = exchangeRateCaptor.getValue();
            assertEquals(testBank, savedEntity.getBank());
            assertEquals(usd.getCode(), savedEntity.getFromCurrencyCode());
            assertEquals(eur.getCode(), savedEntity.getToCurrencyCode());
            assertEquals(createDto.getRate(), savedEntity.getRate());

            verify(exchangeRateCache).evict(eq("1-USD-EUR")); // Verify cache eviction
        }

        @Test
        @DisplayName("Should throw CurrencyNotFoundException when bank not found")
        void createExchangeRateWithCodes_BankNotFound() {
            when(bankRepository.findById(99L)).thenReturn(Optional.empty());

            CurrencyNotFoundException exception = assertThrows(CurrencyNotFoundException.class, () ->
                    exchangeRateService.createExchangeRateWithCodes(99L, usd.getCode(), eur.getCode(), createDto.getRate())
            );

            assertEquals("Bank not found with id: 99", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            verify(exchangeRateCache, never()).evict(anyString());
        }

        @Test
        @DisplayName("Should throw InvalidInputDataException when 'from' currency not found")
        void createExchangeRateWithCodes_FromCurrencyNotFound() {
            when(bankRepository.findById(testBank.getId())).thenReturn(Optional.of(testBank));
            when(currencyRepository.findByCode("XXX")).thenReturn(null); // From currency not found
            //when(currencyRepository.findByCode(eur.getCode())).thenReturn(eur); // To currency found

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.createExchangeRateWithCodes(testBank.getId(), "XXX", eur.getCode(), createDto.getRate())
            );

            assertEquals("Invalid 'from' currency code: XXX", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            verify(exchangeRateCache, never()).evict(anyString());
        }

        @Test
        @DisplayName("Should throw InvalidInputDataException when 'to' currency not found")
        void createExchangeRateWithCodes_ToCurrencyNotFound() {
            when(bankRepository.findById(testBank.getId())).thenReturn(Optional.of(testBank));
            when(currencyRepository.findByCode(usd.getCode())).thenReturn(usd); // From currency found
            when(currencyRepository.findByCode("YYY")).thenReturn(null); // To currency not found

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.createExchangeRateWithCodes(testBank.getId(), usd.getCode(), "YYY", createDto.getRate())
            );

            assertEquals("Invalid 'to' currency code: YYY", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            verify(exchangeRateCache, never()).evict(anyString());
        }


        @Test
        @DisplayName("Should throw InvalidInputDataException when duplicate rate exists")
        void createExchangeRateWithCodes_DuplicateRate() {
            when(bankRepository.findById(testBank.getId())).thenReturn(Optional.of(testBank));
            when(currencyRepository.findByCode(usd.getCode())).thenReturn(usd);
            when(currencyRepository.findByCode(eur.getCode())).thenReturn(eur);
            // Simulate duplicate found
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.of(testRateUsdEur));

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.createExchangeRateWithCodes(testBank.getId(), usd.getCode(), eur.getCode(), createDto.getRate())
            );

            assertEquals("Exchange rate from USD to EUR already exists for this bank.", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            verify(exchangeRateCache, never()).evict(anyString());
        }
    }

    // --- Тесты для createExchangeRatesBulk ---
    @Nested
    @DisplayName("createExchangeRatesBulk Tests")
    class CreateBulkTests {

        private ExchangeRateCreateRequestDto createDtoGbpUsd;

        @BeforeEach
        void bulkSetup() {
            createDtoGbpUsd = new ExchangeRateCreateRequestDto();
            createDtoGbpUsd.setBankId(testBank.getId());
            createDtoGbpUsd.setFromCurrencyCode(gbp.getCode());
            createDtoGbpUsd.setToCurrencyCode(usd.getCode());
            createDtoGbpUsd.setRate(new BigDecimal("1.2500"));
        }

        @Test
        @DisplayName("Should create multiple rates successfully")
        void createBulk_Success() {
            List<ExchangeRateCreateRequestDto> requests = Arrays.asList(createDto, createDtoGbpUsd);

            // Mock finding banks and currencies
            when(bankRepository.findAllById(eq(Collections.singleton(testBank.getId()))))
                    .thenReturn(Collections.singletonList(testBank));
            when(currencyRepository.findAll()).thenReturn(Arrays.asList(usd, eur, gbp)); // Return all needed

            // Mock duplicate checks (none found initially)
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    eq(testBank.getId()), eq(usd.getCode()), eq(eur.getCode()))).thenReturn(Optional.empty());
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    eq(testBank.getId()), eq(gbp.getCode()), eq(usd.getCode()))).thenReturn(Optional.empty());

            // Mock saveAll
            when(exchangeRateRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<ExchangeRate> ratesToSave = invocation.getArgument(0);
                ratesToSave.get(0).setId(101L);
                ratesToSave.get(1).setId(102L);
                return ratesToSave;
            });

            List<ExchangeRate> result = exchangeRateService.createExchangeRatesBulk(requests);

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(101L, result.get(0).getId());
            assertEquals(102L, result.get(1).getId());

            verify(exchangeRateRepository).saveAll(exchangeRateListCaptor.capture());
            List<ExchangeRate> savedList = exchangeRateListCaptor.getValue();
            assertEquals(2, savedList.size());
            assertEquals(usd.getCode(), savedList.get(0).getFromCurrencyCode());
            assertEquals(gbp.getCode(), savedList.get(1).getFromCurrencyCode());

            // Verify cache eviction for both created rates
            verify(exchangeRateCache).evict(eq("1-USD-EUR"));
            verify(exchangeRateCache).evict(eq("1-GBP-USD"));
        }

        @Test
        @DisplayName("Should return empty list when input list is empty")
        void createBulk_EmptyInput() {
            List<ExchangeRate> result = exchangeRateService.createExchangeRatesBulk(new ArrayList<>());
            assertTrue(result.isEmpty());
            verify(exchangeRateRepository, never()).saveAll(anyList());
            verify(exchangeRateCache, never()).evict(anyString());
        }

        @Test
        @DisplayName("Should throw CurrencyNotFoundException if a bank is not found")
        void createBulk_BankNotFound() {
            ExchangeRateCreateRequestDto badDto = new ExchangeRateCreateRequestDto();
            badDto.setBankId(99L); // Non-existent bank
            badDto.setFromCurrencyCode(usd.getCode());
            badDto.setToCurrencyCode(eur.getCode());
            badDto.setRate(BigDecimal.ONE);

            List<ExchangeRateCreateRequestDto> requests = Arrays.asList(createDto, badDto);

            // Mock finding banks (one is missing)
            when(bankRepository.findAllById(eq(Set.of(1L, 99L))))
                    .thenReturn(Collections.singletonList(testBank)); // Only returns the valid one
            when(currencyRepository.findAll()).thenReturn(Arrays.asList(usd, eur));

            CurrencyNotFoundException exception = assertThrows(CurrencyNotFoundException.class, () ->
                    exchangeRateService.createExchangeRatesBulk(requests)
            );

            assertEquals("Bank not found with id: 99", exception.getMessage());
            verify(exchangeRateRepository, never()).saveAll(anyList());
            verify(exchangeRateCache, never()).evict(anyString());
        }

        @Test
        @DisplayName("Should throw InvalidInputDataException if a currency is not found")
        void createBulk_CurrencyNotFound() {
            createDto.setFromCurrencyCode("XXX"); // Invalid currency
            List<ExchangeRateCreateRequestDto> requests = Collections.singletonList(createDto);

            when(bankRepository.findAllById(anySet())).thenReturn(Collections.singletonList(testBank));
            when(currencyRepository.findAll()).thenReturn(Arrays.asList(eur)); // Missing USD

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.createExchangeRatesBulk(requests)
            );

            assertTrue(exception.getMessage().contains("Invalid 'from' currency code: XXX"));
            verify(exchangeRateRepository, never()).saveAll(anyList());
            verify(exchangeRateCache, never()).evict(anyString());
        }


        @Test
        @DisplayName("Should throw InvalidInputDataException if a duplicate rate exists in DB")
        void createBulk_DuplicateRateFound() {
            List<ExchangeRateCreateRequestDto> requests = Collections.singletonList(createDto); // Request to create USD-EUR

            when(bankRepository.findAllById(anySet())).thenReturn(Collections.singletonList(testBank));
            when(currencyRepository.findAll()).thenReturn(Arrays.asList(usd, eur));
            // Simulate USD-EUR already exists
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.of(testRateUsdEur));

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.createExchangeRatesBulk(requests)
            );

            assertEquals("Exchange rate from USD to EUR already exists for this bank.", exception.getMessage());
            verify(exchangeRateRepository, never()).saveAll(anyList());
            verify(exchangeRateCache, never()).evict(anyString());
        }
    }


    // --- Тесты для updateExchangeRate ---
    @Nested
    @DisplayName("updateExchangeRate Tests")
    class UpdateExchangeRateTests {

        @Test
        @DisplayName("Should update rate successfully")
        void update_Success() {
            Long rateIdToUpdate = testRateUsdEur.getId();
            String newFromCode = "GBP";
            String newToCode = "USD";
            BigDecimal newRate = new BigDecimal("1.2500");

            when(exchangeRateRepository.findById(rateIdToUpdate)).thenReturn(Optional.of(testRateUsdEur));
            when(currencyRepository.findByCode(newFromCode)).thenReturn(gbp); // New currency GBP
            when(currencyRepository.findByCode(newToCode)).thenReturn(usd); // New currency USD
            // Check for duplicates with the *new* pair (none found)
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), newFromCode, newToCode)).thenReturn(Optional.empty());
            when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));


            ExchangeRate result = exchangeRateService.updateExchangeRate(rateIdToUpdate, newFromCode, newToCode, newRate);

            assertNotNull(result);
            assertEquals(rateIdToUpdate, result.getId());
            assertEquals(newFromCode, result.getFromCurrencyCode());
            assertEquals(newToCode, result.getToCurrencyCode());
            assertEquals(newRate, result.getRate());
            assertEquals(testBank, result.getBank()); // Bank should not change

            verify(exchangeRateRepository).save(exchangeRateCaptor.capture());
            ExchangeRate savedEntity = exchangeRateCaptor.getValue();
            assertEquals(newFromCode, savedEntity.getFromCurrencyCode());
            assertEquals(newToCode, savedEntity.getToCurrencyCode());
            assertEquals(newRate, savedEntity.getRate());

            // Verify cache eviction for old and potentially new key
            verify(exchangeRateCache).evict(eq("1-USD-EUR")); // Old key
            verify(exchangeRateCache).evict(eq("1-GBP-USD")); // New key
        }

        @Test
        @DisplayName("Should throw CurrencyNotFoundException when rate to update not found")
        void update_RateNotFound() {
            when(exchangeRateRepository.findById(999L)).thenReturn(Optional.empty());

            CurrencyNotFoundException exception = assertThrows(CurrencyNotFoundException.class, () ->
                    exchangeRateService.updateExchangeRate(999L, usd.getCode(), eur.getCode(), BigDecimal.ONE)
            );

            assertEquals("Exchange Rate not found with id: 999", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            verify(exchangeRateCache, never()).evict(anyString());
        }

        @Test
        @DisplayName("Should throw InvalidInputDataException when new 'from' currency invalid")
        void update_InvalidFromCurrency() {
            when(exchangeRateRepository.findById(testRateUsdEur.getId())).thenReturn(Optional.of(testRateUsdEur));
            when(currencyRepository.findByCode("XXX")).thenReturn(null); // Invalid from currency

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.updateExchangeRate(testRateUsdEur.getId(), "XXX", eur.getCode(), BigDecimal.ONE)
            );

            assertEquals("Invalid 'from' currency code: XXX", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            verify(exchangeRateCache, never()).evict(anyString()); // Cache for old key is evicted *before* validation fail
        }


        @Test
        @DisplayName("Should throw InvalidInputDataException when update creates duplicate")
        void update_CreatesDuplicate() {
            Long rateIdToUpdate = testRateUsdEur.getId(); // Updating USD-EUR
            String newFromCode = "GBP"; // Trying to change it to GBP-USD
            String newToCode = "USD";
            BigDecimal newRate = new BigDecimal("1.2500");

            // Assume another rate (ID 200) already exists for GBP-USD
            ExchangeRate existingGbpUsd = ExchangeRate.builder().id(200L).bank(testBank).fromCurrencyCode("GBP").toCurrencyCode("USD").rate(BigDecimal.TEN).build();

            when(exchangeRateRepository.findById(rateIdToUpdate)).thenReturn(Optional.of(testRateUsdEur));
            when(currencyRepository.findByCode(newFromCode)).thenReturn(gbp);
            when(currencyRepository.findByCode(newToCode)).thenReturn(usd);
            // Simulate finding the *other* existing rate when checking for duplicates
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), newFromCode, newToCode)).thenReturn(Optional.of(existingGbpUsd));

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.updateExchangeRate(rateIdToUpdate, newFromCode, newToCode, newRate)
            );

            assertEquals("Another exchange rate from GBP to USD already exists for this bank.", exception.getMessage());
            verify(exchangeRateRepository, never()).save(any());
            //verify(exchangeRateCache).evict(eq("1-USD-EUR")); // Old cache key evicted
            verify(exchangeRateCache, never()).evict(eq("1-GBP-USD")); // New key not evicted as save failed
        }
    }

    // --- Тесты для getExchangeRateValue ---
    @Nested
    @DisplayName("getExchangeRateValue Tests")
    class GetValueTests {

        private final String cacheKey = "1-USD-EUR";
        private final BigDecimal rateValue = new BigDecimal("0.9000");

        @Test
        @DisplayName("Should return value from cache when cache hit")
        void getValue_CacheHit() {
            when(exchangeRateCache.get(cacheKey)).thenReturn(rateValue); // Cache hit

            BigDecimal result = exchangeRateService.getExchangeRateValue(testBank.getId(), usd.getCode(), eur.getCode());

            assertEquals(rateValue, result);
            verify(exchangeRateCache).get(cacheKey);
            verify(exchangeRateRepository, never()).findByBankIdAndFromCurrencyCodeAndToCurrencyCode(anyLong(), anyString(), anyString());
            verify(exchangeRateCache, never()).put(anyString(), any());
        }

        @Test
        @DisplayName("Should return value from DB and put in cache when cache miss")
        void getValue_CacheMiss_DbHit() {
            when(exchangeRateCache.get(cacheKey)).thenReturn(null); // Cache miss
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.of(testRateUsdEur));

            BigDecimal result = exchangeRateService.getExchangeRateValue(testBank.getId(), usd.getCode(), eur.getCode());

            assertEquals(testRateUsdEur.getRate(), result);
            verify(exchangeRateCache).get(cacheKey);
            verify(exchangeRateRepository).findByBankIdAndFromCurrencyCodeAndToCurrencyCode(testBank.getId(), usd.getCode(), eur.getCode());
            verify(exchangeRateCache).put(cacheKeyCaptor.capture(), cacheValueCaptor.capture());
            assertEquals(cacheKey, cacheKeyCaptor.getValue());
            assertEquals(testRateUsdEur.getRate(), cacheValueCaptor.getValue());
        }

        @Test
        @DisplayName("Should return null when rate not found in DB and cache miss")
        void getValue_CacheMiss_DbMiss() {
            when(exchangeRateCache.get(cacheKey)).thenReturn(null); // Cache miss
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.empty()); // DB miss

            BigDecimal result = exchangeRateService.getExchangeRateValue(testBank.getId(), usd.getCode(), eur.getCode());

            assertNull(result);
            verify(exchangeRateCache).get(cacheKey);
            verify(exchangeRateRepository).findByBankIdAndFromCurrencyCodeAndToCurrencyCode(testBank.getId(), usd.getCode(), eur.getCode());
            verify(exchangeRateCache, never()).put(anyString(), any()); // Should not cache null/absence
        }

        @Test
        @DisplayName("Should not use cache when cacheEnabled is false")
        void getValue_CacheDisabled() {
            ReflectionTestUtils.setField(exchangeRateService, "cacheEnabled", false); // Disable cache

            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.of(testRateUsdEur));

            BigDecimal result = exchangeRateService.getExchangeRateValue(testBank.getId(), usd.getCode(), eur.getCode());

            assertEquals(testRateUsdEur.getRate(), result);
            verify(exchangeRateCache, never()).get(anyString());
            verify(exchangeRateRepository).findByBankIdAndFromCurrencyCodeAndToCurrencyCode(testBank.getId(), usd.getCode(), eur.getCode());
            verify(exchangeRateCache, never()).put(anyString(), any());
        }
        @Test
        @DisplayName("Should evict invalid cache entry and fetch from DB")
        void getValue_InvalidCacheEntryType() {
            when(exchangeRateCache.get(cacheKey)).thenReturn("not a BigDecimal"); // Invalid entry
            when(exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    testBank.getId(), usd.getCode(), eur.getCode())).thenReturn(Optional.of(testRateUsdEur)); // DB has correct value

            BigDecimal result = exchangeRateService.getExchangeRateValue(testBank.getId(), usd.getCode(), eur.getCode());

            assertEquals(testRateUsdEur.getRate(), result);
            verify(exchangeRateCache).get(cacheKey);
            verify(exchangeRateCache).evict(cacheKey); // Verify eviction of invalid entry
            verify(exchangeRateRepository).findByBankIdAndFromCurrencyCodeAndToCurrencyCode(testBank.getId(), usd.getCode(), eur.getCode());
            verify(exchangeRateCache).put(cacheKey, testRateUsdEur.getRate()); // Verify putting correct entry
        }

    }

    // --- Тесты для getMinRate ---
    @Nested
    @DisplayName("getMinRate Tests")
    class GetMinRateTests {

        @Test
        @DisplayName("Should return min rate when found")
        void getMinRate_Success() {
            ExchangeRate minRate = ExchangeRate.builder().id(200L).rate(new BigDecimal("1.20")).fromCurrencyCode("GBP").toCurrencyCode("USD").build();
            ExchangeRate higherRate = ExchangeRate.builder().id(201L).rate(new BigDecimal("1.25")).fromCurrencyCode("GBP").toCurrencyCode("USD").build();

            when(currencyRepository.findByCode("GBP")).thenReturn(gbp);
            when(currencyRepository.findByCode("USD")).thenReturn(usd);
            // findMinRate repository method is expected to return the one with the lowest rate
            when(exchangeRateRepository.findMinRate("GBP", "USD")).thenReturn(Collections.singletonList(minRate));

            ExchangeRate result = exchangeRateService.getMinRate("GBP", "USD");

            assertNotNull(result);
            assertEquals(minRate.getId(), result.getId());
            assertEquals(minRate.getRate(), result.getRate());
            verify(exchangeRateRepository).findMinRate("GBP", "USD");
        }

        @Test
        @DisplayName("Should return null when no rate found")
        void getMinRate_NotFound() {
            when(currencyRepository.findByCode("GBP")).thenReturn(gbp);
            when(currencyRepository.findByCode("EUR")).thenReturn(eur);
            when(exchangeRateRepository.findMinRate("GBP", "EUR")).thenReturn(new ArrayList<>()); // Empty list

            ExchangeRate result = exchangeRateService.getMinRate("GBP", "EUR");

            assertNull(result);
            verify(exchangeRateRepository).findMinRate("GBP", "EUR");
        }

        @Test
        @DisplayName("Should throw InvalidInputDataException for invalid 'from' currency")
        void getMinRate_InvalidFromCurrency() {
            when(currencyRepository.findByCode("XXX")).thenReturn(null); // Invalid 'from'

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.getMinRate("XXX", "USD")
            );
            assertEquals("Invalid 'from' currency code: XXX", exception.getMessage());
            verify(exchangeRateRepository, never()).findMinRate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw InvalidInputDataException for invalid 'to' currency")
        void getMinRate_InvalidToCurrency() {
            when(currencyRepository.findByCode("GBP")).thenReturn(gbp);
            when(currencyRepository.findByCode("YYY")).thenReturn(null); // Invalid 'to'

            InvalidInputDataException exception = assertThrows(InvalidInputDataException.class, () ->
                    exchangeRateService.getMinRate("GBP", "YYY")
            );
            assertEquals("Invalid 'to' currency code: YYY", exception.getMessage());
            verify(exchangeRateRepository, never()).findMinRate(anyString(), anyString());
        }
    }


    // --- Тесты для deleteExchangeRate ---
    @Nested
    @DisplayName("deleteExchangeRate Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete rate and clear cache when rate exists")
        void delete_Success() {
            Long rateId = testRateUsdEur.getId();
            when(exchangeRateRepository.findById(rateId)).thenReturn(Optional.of(testRateUsdEur));
            doNothing().when(exchangeRateRepository).deleteById(rateId); // Mock void method

            boolean result = exchangeRateService.deleteExchangeRate(rateId);

            assertTrue(result);
            verify(exchangeRateRepository).findById(rateId);
            verify(exchangeRateRepository).deleteById(rateId);
            verify(exchangeRateCache).evict(eq("1-USD-EUR"));
        }

        @Test
        @DisplayName("Should return false when rate does not exist")
        void delete_NotFound() {
            Long rateId = 999L;
            when(exchangeRateRepository.findById(rateId)).thenReturn(Optional.empty());

            boolean result = exchangeRateService.deleteExchangeRate(rateId);

            assertFalse(result);
            verify(exchangeRateRepository).findById(rateId);
            verify(exchangeRateRepository, never()).deleteById(anyLong());
            verify(exchangeRateCache, never()).evict(anyString());
        }
    }

    // --- Тесты для getAllExchangeRates ---
    @Test
    @DisplayName("getAllExchangeRates should return list from repository")
    void getAllExchangeRates_Success() {
        List<ExchangeRate> rates = Arrays.asList(testRateUsdEur, ExchangeRate.builder().id(102L).build());
        when(exchangeRateRepository.findAll()).thenReturn(rates);

        List<ExchangeRate> result = exchangeRateService.getAllExchangeRates();

        assertEquals(2, result.size());
        assertSame(rates, result); // Check if it's the exact same list object
        verify(exchangeRateRepository).findAll();
        // Optional: Verify caching if implemented for this method
        // verify(exchangeRateCache).put(eq("/exchange-rates"), eq(rates));
    }

    // --- Тесты для getAllExchangeRatesByBankId ---
    @Test
    @DisplayName("getAllExchangeRatesByBankId should return list from repository")
    void getAllExchangeRatesByBankId_Success() {
        Long bankId = testBank.getId();
        List<ExchangeRate> rates = Collections.singletonList(testRateUsdEur);
        when(exchangeRateRepository.findByBankId(bankId)).thenReturn(rates);

        List<ExchangeRate> result = exchangeRateService.getAllExchangeRatesByBankId(bankId);

        assertEquals(1, result.size());
        assertSame(rates, result);
        verify(exchangeRateRepository).findByBankId(bankId);
        // Optional: Verify caching if implemented
    }

}