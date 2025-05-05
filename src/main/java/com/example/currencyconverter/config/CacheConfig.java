package com.example.currencyconverter.config;

import com.example.currencyconverter.utils.InMemoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${cache.max-size:5242}")
    private int cacheSize;

    @Bean
    public InMemoryCache<String, Object> applicationCache() {
        logger.info("Creating applicationCache with max size: {}", cacheSize);
        return new InMemoryCache<String, Object>(cacheSize);
    }

    public double getCacheSizeInKB() {
        return 1.0;
    }
}