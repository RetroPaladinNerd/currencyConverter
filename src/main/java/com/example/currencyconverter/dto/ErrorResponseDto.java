package com.example.currencyconverter.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDto {
    private LocalDateTime timestamp;
    private int status;
    private String error; // Краткое описание статуса (e.g., "Bad Request")
    private String message; // Общее сообщение об ошибке
    private String path; // Путь запроса
    private Map<String, List<String>> details; // Детали ошибок валидации (поле -> список ошибок)
}