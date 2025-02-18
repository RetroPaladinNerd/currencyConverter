package com.example.currencyconverter.exception;

public class FetchExchangeRatesException extends RuntimeException {

    public FetchExchangeRatesException(String message) {
        super(message);
    }
}