package com.example.currencyconverter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Currency Converter API")
                        .version("1.0.0")
                        .description("API for managing banks, currencies, exchange rates, and performing currency conversions.")
                        .termsOfService("http://example.com/terms/") // Замени на реальную ссылку или удали
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))); // Замени или удали
    }
}