package com.example.currencyconverter.model;

import lombok.Data;

@Data
public class Valute {

    private String id;
    private String numCode;
    private String charCode;
    private int nominal;
    private String name;
    private double value;
    private double previous;
}