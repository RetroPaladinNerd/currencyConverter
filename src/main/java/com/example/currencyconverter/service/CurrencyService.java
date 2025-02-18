package com.example.currencyconverter.service;

import com.example.currencyconverter.config.CbrConfig;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.model.ConversionRequest;
import com.example.currencyconverter.model.ConversionResponse;
import com.example.currencyconverter.model.ExchangeRateResponse;
import com.example.currencyconverter.model.Valute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class CurrencyService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyService.class);

    private final CbrConfig cbrConfig;
    private final RestTemplate restTemplate;

    public CurrencyService(CbrConfig cbrConfig) {
        this.cbrConfig = cbrConfig;
        this.restTemplate = new RestTemplate();
    }

    @Cacheable("exchangeRates")
    public Map<String, Double> getExchangeRates() {
        String apiUrl = cbrConfig.getApiUrl();
        logger.info("Fetching exchange rates from: {}", apiUrl);

        String xmlData = restTemplate.getForObject(apiUrl, String.class);

        if (xmlData != null) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)));
                doc.getDocumentElement().normalize();

                NodeList valutes = doc.getElementsByTagName("Valute");
                Map<String, Double> exchangeRates = new HashMap<>();
                exchangeRates.put("RUB", 1.0); // Добавляем курс рубля к рублю

                for (int i = 0; i < valutes.getLength(); i++) {
                    Element valuteElement = (Element) valutes.item(i);
                    String charCode = valuteElement.getElementsByTagName("CharCode").item(0).getTextContent();
                    double value = Double.parseDouble(valuteElement.getElementsByTagName("Value").item(0).getTextContent().replace(",", "."));
                    int nominal = Integer.parseInt(valuteElement.getElementsByTagName("Nominal").item(0).getTextContent());

                    exchangeRates.put(charCode, value / nominal); // Нормализуем курс, учитывая номинал
                }

                return exchangeRates;

            } catch (Exception e) {
                logger.error("Error parsing XML: ", e);
                return null;
            }
        } else {
            logger.warn("Failed to fetch data from CBR API");
            return null;
        }
    }

    public ExchangeRateResponse getExchangeRate(String fromCurrency, String toCurrency) {
        Map<String, Double> exchangeRates = getExchangeRates();

        if (exchangeRates == null) {
            throw new RuntimeException("Failed to fetch exchange rates");
        }

        if (!exchangeRates.containsKey(fromCurrency) || !exchangeRates.containsKey(toCurrency)) {
            throw new CurrencyNotFoundException("Invalid currency code");
        }

        double fromRate = exchangeRates.get(fromCurrency);
        double toRate = exchangeRates.get(toCurrency);

        logger.debug("fromCurrency: {}, toCurrency: {}", fromCurrency, toCurrency);
        logger.debug("fromRate: {}, toRate: {}", fromRate, toRate);

        // Проверяем, что валюты не равны
        if (fromCurrency.equals(toCurrency)) {
            return ExchangeRateResponse.builder()
                    .fromCurrency(fromCurrency)
                    .toCurrency(toCurrency)
                    .exchangeRate(1.0) // Если валюты одинаковы, курс равен 1
                    .build();
        }


        double exchangeRate = fromRate / toRate;
        logger.debug("exchangeRate: {}", exchangeRate);


        return ExchangeRateResponse.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .exchangeRate(exchangeRate)
                .build();
    }

    public ConversionResponse convertCurrency(ConversionRequest request) {
        ExchangeRateResponse exchangeRateResponse = getExchangeRate(request.getFromCurrency(), request.getToCurrency());
        double convertedAmount = request.getAmount() * exchangeRateResponse.getExchangeRate();

        // Форматируем convertedAmount до трех знаков после запятой
        BigDecimal bd = new BigDecimal(convertedAmount).setScale(3, RoundingMode.HALF_UP);
        convertedAmount = bd.doubleValue();

        return ConversionResponse.builder()
                .fromCurrency(request.getFromCurrency())
                .toCurrency(request.getToCurrency())
                .amount(request.getAmount())
                .convertedAmount(convertedAmount)
                .build();
    }
}