package com.example.currencyconverter.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogJobStatusDto {
    private String jobId;
    private LogJobStatus status;
    private String filePath;
    private String errorMessage;
}