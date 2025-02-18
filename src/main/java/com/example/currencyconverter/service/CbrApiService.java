package com.example.currencyconverter.service;

import com.example.currencyconverter.config.CbrConfig;
import com.example.currencyconverter.exception.CurrencyNotFoundException;
import com.example.currencyconverter.exception.CbrApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbrApiService {

    private final CbrConfig cbrConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    //private final ObjectMapper objectMapper = new ObjectMapper(); //Not using objectMapper

    @Cacheable("exchangeRates")
    public double getExchangeRate(String fromCurrency, String toCurrency) {
        try {
            String apiUrl = cbrConfig.getApiUrl();
            //String apiUrl = String.format("%s%s", myfinConfig.getApiUrl(), MyfinConstants.MYFIN_EXCHANGE_RATE_URL); //wrong API

            // Fetching XML data from CBR
            String xmlData = restTemplate.getForObject(apiUrl, String.class);

            if (xmlData == null) {
                throw new CbrApiException("Failed to fetch data from CBR API"); //todo: change exception name.
            }

            Map<String, Double> exchangeRates = parseXml(xmlData);


            if (!exchangeRates.containsKey(fromCurrency)) {
                throw new CurrencyNotFoundException("Currency not found: " + fromCurrency);
            }
            if (!exchangeRates.containsKey(toCurrency)) {
                throw new CurrencyNotFoundException("Currency not found: " + toCurrency);
            }

            double fromRate = exchangeRates.get(fromCurrency);
            double toRate = exchangeRates.get(toCurrency);

            return toRate / fromRate;


        } catch (CurrencyNotFoundException e) {
            throw e;
        }  catch (Exception e) {
            log.error("Error calling CBR API", e);
            throw new CbrApiException("Error calling CBR API: " + e.getMessage()); //todo:Change exception name
        }
    }


    private Map<String, Double> parseXml(String xmlData) {
        Map<String, Double> exchangeRates = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            NodeList valutes = doc.getElementsByTagName("Valute");

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
            log.error("Error parsing XML: ", e);
            return null;
        }
    }
}