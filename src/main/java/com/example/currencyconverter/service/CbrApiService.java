package com.example.currencyconverter.service;

import com.example.currencyconverter.config.CbrConfig;
import com.example.currencyconverter.exception.CbrApiException;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.model.CbrJsonResponse;
import com.example.currencyconverter.model.Valute;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
@Slf4j
public class CbrApiService {

    private final CbrConfig cbrConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    @Cacheable("exchangeRates")
    public double getExchangeRate(String fromCurrency, String toCurrency) {
        try {
            String apiUrl = cbrConfig.getApiUrl();

            CbrJsonResponse response = restTemplate
                    .getForObject(apiUrl, CbrJsonResponse.class); // Use CbrJsonResponse

            if (response == null || response.getValute() == null) {
                throw new CbrApiException("Failed to fetch data from CBR API");
            }

            Map<String, Valute> valutes = response.getValute();


            if (fromCurrency.equalsIgnoreCase("RUB")) {
                valutes.put("RUB", createRubValute());
            }

            if (toCurrency.equalsIgnoreCase("RUB")) {
                valutes.put("RUB", createRubValute());
            }

            if (!valutes.containsKey(fromCurrency)) {
                throw new CurrencyNotFoundException("Currency not found: " + fromCurrency);
            }
            if (!valutes.containsKey(toCurrency)) {
                throw new CurrencyNotFoundException("Currency not found: " + toCurrency);
            }

            double fromRate = fromCurrency.equalsIgnoreCase("RUB")
                    ? 1.0 : valutes.get(fromCurrency).getValue();
            double toRate = toCurrency.equalsIgnoreCase("RUB")
                    ? 1.0 : valutes.get(toCurrency).getValue();

            return toRate / fromRate;

        } catch (CurrencyNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling CBR API", e);
            throw new CbrApiException("Error calling CBR API: " + e.getMessage());
        }
    }

    private Valute createRubValute() {
        Valute rub = new Valute();
        rub.setId("R00000");
        rub.setNumCode("643");
        rub.setCharCode("RUB");
        rub.setNominal(1);
        rub.setName("Российский рубль");
        rub.setValue(1.0);
        rub.setPrevious(1.0);
        return rub;
    }

}