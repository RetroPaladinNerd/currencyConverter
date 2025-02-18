package com.example.currencyconverter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cbr.api")
@Data
public class CbrConfig {

    private String apiUrl;
    private int timeout;
}