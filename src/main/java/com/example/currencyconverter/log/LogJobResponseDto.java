package com.example.currencyconverter.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogJobResponseDto {
    private String jobId;
    private String message;
}