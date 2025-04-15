package com.example.currencyconverter.service;

import com.example.currencyconverter.dto.ExchangeRateCreateRequestDto; // Добавлен импорт
import com.example.currencyconverter.entity.Bank;
import com.example.currencyconverter.entity.Currency;
import com.example.currencyconverter.entity.ExchangeRate;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.exception.InvalidInputDataException;
import com.example.currencyconverter.repository.BankRepository;
import com.example.currencyconverter.repository.CurrencyRepository;
import com.example.currencyconverter.repository.ExchangeRateRepository;
import com.example.currencyconverter.utils.InMemoryCache; // Убедись, что InMemoryCache существует и настроен
import java.math.BigDecimal;
import java.util.ArrayList; // Добавлен импорт
import java.util.List;
import java.util.Map; // Добавлен импорт
import java.util.Optional;
import java.util.Set; // Добавлен импорт
import java.util.function.Function; // Добавлен импорт
import java.util.stream.Collectors; // Добавлен импорт
import java.util.stream.Stream;       // <--- И ЭТОТ ИМПОРТ
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;
    // Убедись, что бин кэша с таким именем ('exchangeRateCache') определен в конфигурации
    private final InMemoryCache<String, Object> exchangeRateCache;

    @Value("${cache.enabled:true}")
    private boolean cacheEnabled;

    private String getCacheKey(Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        return bankId + "-" + fromCurrencyCode + "-" + toCurrencyCode;
    }

    // --- МЕТОДЫ ДЛЯ СОЗДАНИЯ/ОБНОВЛЕНИЯ С КОДАМИ ВАЛЮТ ---

    @Transactional
    public ExchangeRate createExchangeRateWithCodes(
            Long bankId, String fromCurrencyCode, String toCurrencyCode, BigDecimal rate) {
        log.debug("Attempting to create exchange rate for bankId: {}, from: {}, to: {}, rate: {}",
                bankId, fromCurrencyCode, toCurrencyCode, rate);

        final Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> {
                    log.warn("Bank not found with id: {}", bankId);
                    return new CurrencyNotFoundException("Bank not found with id: " + bankId);
                });

        validateCurrencyCode(fromCurrencyCode, "from");
        validateCurrencyCode(toCurrencyCode, "to");

        // Проверка на дубликат
        validateDuplicateRate(bankId, fromCurrencyCode, toCurrencyCode);

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .bank(bank)
                .fromCurrencyCode(fromCurrencyCode) // Используем проверенный код
                .toCurrencyCode(toCurrencyCode)   // Используем проверенный код
                .rate(rate)
                .build();

        ExchangeRate savedRate = exchangeRateRepository.save(exchangeRate);
        log.info("Successfully created exchange rate with id: {}", savedRate.getId());
        clearCacheForRate(bankId, fromCurrencyCode, toCurrencyCode); // Очистка кэша
        return savedRate;
    }


    /**
     * Creates multiple exchange rates in a single transaction using Stream API.
     *
     * @param requests List of DTOs containing data for new exchange rates.
     * @return List of created ExchangeRate entities.
     * @throws InvalidInputDataException if any validation fails (bank/currency not found, duplicate rate).
     *                                   The transaction will be rolled back.
     */
    @Transactional
    public List<ExchangeRate> createExchangeRatesBulk(List<ExchangeRateCreateRequestDto> requests) {
        log.info("Attempting to create {} exchange rates in bulk.", requests.size());
        if (requests == null || requests.isEmpty()) {
            log.warn("Bulk create request list is null or empty.");
            return new ArrayList<>(); // Возвращаем пустой список, если вход пуст
        }

        // 1. Предварительная загрузка и проверка необходимых данных (оптимизация)
        // Собираем все необходимые ID банков и коды валют
        Set<Long> bankIds = requests.stream()
                .map(ExchangeRateCreateRequestDto::getBankId)
                .collect(Collectors.toSet());
        Set<String> currencyCodes = requests.stream()
                .flatMap(r -> Stream.of(r.getFromCurrencyCode(), r.getToCurrencyCode()))
                .collect(Collectors.toSet());

        // Загружаем банки одним запросом
        Map<Long, Bank> banksMap = bankRepository.findAllById(bankIds).stream()
                .collect(Collectors.toMap(Bank::getId, Function.identity()));

        // Загружаем валюты одним запросом
        Map<String, Currency> currenciesMap = currencyRepository.findAll().stream() // Предполагая, что валют не слишком много
                .filter(c -> currencyCodes.contains(c.getCode())) // Фильтруем по нужным кодам
                .collect(Collectors.toMap(Currency::getCode, Function.identity()));


        // 2. Обработка каждого запроса с использованием Stream API
        List<ExchangeRate> entitiesToSave = requests.stream()
                .map(request -> {
                    // Валидация наличия банка
                    Bank bank = banksMap.get(request.getBankId());
                    if (bank == null) {
                        log.error("Bank not found for ID: {} in bulk request.", request.getBankId());
                        throw new CurrencyNotFoundException("Bank not found with id: " + request.getBankId());
                    }

                    // Валидация валют
                    if (!currenciesMap.containsKey(request.getFromCurrencyCode())) {
                        log.error("FromCurrency not found for code: {} in bulk request.", request.getFromCurrencyCode());
                        throw new InvalidInputDataException("Invalid 'from' currency code: " + request.getFromCurrencyCode());
                    }
                    if (!currenciesMap.containsKey(request.getToCurrencyCode())) {
                        log.error("ToCurrency not found for code: {} in bulk request.", request.getToCurrencyCode());
                        throw new InvalidInputDataException("Invalid 'to' currency code: " + request.getToCurrencyCode());
                    }

                    // Проверка на дубликат (в базе данных, в рамках транзакции)
                    validateDuplicateRate(request.getBankId(), request.getFromCurrencyCode(), request.getToCurrencyCode());
                    // TODO: Добавить проверку на дубликаты *внутри* самой batch-заявки, если это необходимо


                    // Создание сущности ExchangeRate
                    return ExchangeRate.builder()
                            .bank(bank)
                            .fromCurrencyCode(request.getFromCurrencyCode())
                            .toCurrencyCode(request.getToCurrencyCode())
                            .rate(request.getRate())
                            .build();
                })
                .collect(Collectors.toList()); // Собираем все подготовленные сущности

        // 3. Сохранение всех сущностей одним вызовом (более эффективно)
        List<ExchangeRate> savedEntities = exchangeRateRepository.saveAll(entitiesToSave);
        log.info("Successfully saved {} exchange rates.", savedEntities.size());

        // 4. Очистка кэша для каждой сохраненной сущности
        savedEntities.forEach(rate -> clearCacheForRate(rate.getBank().getId(), rate.getFromCurrencyCode(), rate.getToCurrencyCode()));
        log.debug("Cache cleared for {} newly created exchange rates.", savedEntities.size());
        // Опционально: Очистить общие кэши, если они используются
        // exchangeRateCache.evict("/exchange-rates");
        // controllerCache.clear(); // Очистить кеш контроллера, если нужно

        return savedEntities;
    }


    // Метод обновления, теперь принимает коды валют
    @Transactional
    public ExchangeRate updateExchangeRate(
            Long id, String fromCurrencyCode, String toCurrencyCode, BigDecimal newRate) {
        log.debug("Attempting to update exchange rate with id: {}, from: {}, to: {}, newRate: {}",
                id, fromCurrencyCode, toCurrencyCode, newRate);

        ExchangeRate exchangeRate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("ExchangeRate not found with id: {}", id);
                    return new CurrencyNotFoundException("Exchange Rate not found with id: " + id);
                });

        // Валидируем коды валют
        validateCurrencyCode(fromCurrencyCode, "from");
        validateCurrencyCode(toCurrencyCode, "to");

        // Проверка на дубликат, если пара банк/валюты изменилась ИЛИ если это другая запись с такой же парой
        if (!exchangeRate.getFromCurrencyCode().equals(fromCurrencyCode) || !exchangeRate.getToCurrencyCode().equals(toCurrencyCode)) {
            exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                    exchangeRate.getBank().getId(), fromCurrencyCode, toCurrencyCode
            ).ifPresent(existing -> {
                // Убедимся, что найденная существующая запись - это не та же самая, которую мы обновляем
                if (!existing.getId().equals(id)) {
                    log.warn("Update would create a duplicate exchange rate for bankId: {}, from: {}, to: {}",
                            exchangeRate.getBank().getId(), fromCurrencyCode, toCurrencyCode);
                    throw new InvalidInputDataException(String.format(
                            "Another exchange rate from %s to %s already exists for this bank.", fromCurrencyCode, toCurrencyCode
                    ));
                }
            });
        }


        // Очищаем старый ключ кэша перед изменением кодов/банка
        clearCacheForRate(exchangeRate.getBank().getId(), exchangeRate.getFromCurrencyCode(), exchangeRate.getToCurrencyCode());

        exchangeRate.setFromCurrencyCode(fromCurrencyCode); // Используем проверенный код
        exchangeRate.setToCurrencyCode(toCurrencyCode);   // Используем проверенный код
        exchangeRate.setRate(newRate);
        // Банк не меняем при обновлении курса, если это не требуется бизнес-логикой

        ExchangeRate updatedRate = exchangeRateRepository.save(exchangeRate);
        log.info("Successfully updated exchange rate with id: {}", updatedRate.getId());
        // Очищаем новый ключ кэша (на всякий случай, если коды изменились)
        clearCacheForRate(updatedRate.getBank().getId(), updatedRate.getFromCurrencyCode(), updatedRate.getToCurrencyCode());
        return updatedRate;
    }


    // --- МЕТОДЫ ПОЛУЧЕНИЯ ДАННЫХ ---

    public List<ExchangeRate> getAllExchangeRates() {
        log.debug("Fetching all exchange rates");
        // TODO: Рассмотреть возможность кэширования этого списка, если он часто запрашивается
        // String cacheKey = "/exchange-rates";
        // List<ExchangeRate> cachedRates = (List<ExchangeRate>) exchangeRateCache.get(cacheKey);
        // if (cacheEnabled && cachedRates != null) return cachedRates;
        List<ExchangeRate> rates = exchangeRateRepository.findAll();
        // if (cacheEnabled) exchangeRateCache.put(cacheKey, rates);
        return rates;
    }

    public List<ExchangeRate> getAllExchangeRatesByBankId(Long bankId) {
        log.debug("Fetching all exchange rates for bankId: {}", bankId);
        // TODO: Кэширование для этого метода может быть полезным
        return exchangeRateRepository.findByBankId(bankId);
    }


    public Optional<ExchangeRate> getExchangeRate(Long id) {
        log.debug("Fetching exchange rate by id: {}", id);
        // TODO: Рассмотреть кэширование по ID
        return exchangeRateRepository.findById(id);
    }

    public BigDecimal getExchangeRateValue(
            Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        log.trace("Attempting to get exchange rate value for bankId: {}, from: {}, to: {}", bankId, fromCurrencyCode, toCurrencyCode);
        String cacheKey = getCacheKey(bankId, fromCurrencyCode, toCurrencyCode);

        if (cacheEnabled) {
            Object cachedValue = exchangeRateCache.get(cacheKey);
            if (cachedValue instanceof BigDecimal) { // Добавлена проверка типа
                log.trace("Cache hit for key: {}", cacheKey);
                return (BigDecimal) cachedValue;
            } else if (cachedValue != null) {
                // Если в кеше что-то есть, но не BigDecimal, удаляем некорректное значение
                log.warn("Invalid cache entry type found for key: {}. Expected BigDecimal, got {}. Evicting.", cacheKey, cachedValue.getClass().getName());
                exchangeRateCache.evict(cacheKey);
            }
            log.trace("Cache miss for key: {}", cacheKey);
        }

        Optional<ExchangeRate> exchangeRateOpt = exchangeRateRepository
                .findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                        bankId, fromCurrencyCode, toCurrencyCode);

        if (exchangeRateOpt.isEmpty()) {
            log.warn("Exchange rate not found in DB for bankId: {}, from: {}, to: {}", bankId, fromCurrencyCode, toCurrencyCode);
            // Не кешируем отсутствие значения, чтобы дать шанс появиться при следующем запросе
            return null;
        }

        BigDecimal rate = exchangeRateOpt.get().getRate();

        if (cacheEnabled) {
            log.trace("Putting value into cache for key: {}", cacheKey);
            exchangeRateCache.put(cacheKey, rate);
        }

        return rate;
    }

    public ExchangeRate getMinRate(String fromCurrencyCode, String toCurrencyCode) {
        log.debug("Finding minimum exchange rate from {} to {}", fromCurrencyCode, toCurrencyCode);
        // Валидация кодов валют
        validateCurrencyCode(fromCurrencyCode, "from");
        validateCurrencyCode(toCurrencyCode, "to");


        List<ExchangeRate> rates = exchangeRateRepository.findMinRate(fromCurrencyCode, toCurrencyCode);
        if (rates.isEmpty()) {
            log.info("No exchange rates found from {} to {}", fromCurrencyCode, toCurrencyCode);
            return null; // Возвращаем null, контроллер обработает как 404 или кинет исключение
        }
        log.debug("Minimum rate found with id: {}", rates.get(0).getId());
        return rates.get(0);
    }


    // --- МЕТОД УДАЛЕНИЯ И ОЧИСТКИ КЭША ---

    @Transactional
    public boolean deleteExchangeRate(Long id) {
        log.debug("Attempting to delete exchange rate with id: {}", id);
        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isPresent()) {
            ExchangeRate rate = rateOpt.get();
            // Очищаем кэш перед удалением
            clearCacheForRate(rate.getBank().getId(), rate.getFromCurrencyCode(), rate.getToCurrencyCode());
            exchangeRateRepository.deleteById(id);
            log.info("Successfully deleted exchange rate with id: {}", id);
            return true;
        } else {
            log.warn("Exchange rate with id: {} not found for deletion.", id);
            return false;
        }
    }

    // --- Вспомогательные методы ---

    // Вспомогательный метод для очистки кэша
    private void clearCacheForRate(Long bankId, String fromCode, String toCode) {
        if (cacheEnabled) {
            String cacheKey = getCacheKey(bankId, fromCode, toCode);
            log.trace("Clearing cache for key: {}", cacheKey);
            exchangeRateCache.evict(cacheKey);
            // Также может потребоваться очистка кэшей, связанных со списками (если они есть),
            // например, кэш getAllExchangeRates или getAllExchangeRatesByBankId
            // exchangeRateCache.evict("/exchange-rates"); // Пример очистки общего кэша
            // exchangeRateCache.evict("/banks/" + bankId); // Пример очистки кэша банка
        }
    }

    // Вспомогательный метод для валидации кода валюты
    private void validateCurrencyCode(String currencyCode, String type) {
        if (currencyRepository.findByCode(currencyCode) == null) {
            log.warn("{}Currency not found with code: {}", type.substring(0, 1).toUpperCase() + type.substring(1), currencyCode);
            throw new InvalidInputDataException(String.format("Invalid '%s' currency code: %s", type, currencyCode));
        }
    }

    // Вспомогательный метод для проверки дубликата курса
    private void validateDuplicateRate(Long bankId, String fromCurrencyCode, String toCurrencyCode) {
        Optional<ExchangeRate> existingRate = exchangeRateRepository.findByBankIdAndFromCurrencyCodeAndToCurrencyCode(
                bankId, fromCurrencyCode, toCurrencyCode
        );
        if (existingRate.isPresent()) {
            log.warn("Duplicate exchange rate detected for bankId: {}, from: {}, to: {}", bankId, fromCurrencyCode, toCurrencyCode);
            throw new InvalidInputDataException(String.format(
                    "Exchange rate from %s to %s already exists for this bank.", fromCurrencyCode, toCurrencyCode
            ));
        }
    }
}