package com.example.currencyconverter.exception;

public class CbrApiException extends RuntimeException{
    public CbrApiException(String message) {
        super(message);
    }
}