package com.example.currencyconverter.exception;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST) // Устанавливаем статус по умолчанию
public class InvalidInputDataException extends RuntimeException {

    private final Map<String, List<String>> errors; // Поле для хранения деталей ошибок

    public InvalidInputDataException(String message) {
        super(message);
        this.errors = Collections.singletonMap("general", Collections.singletonList(message));
    }

    public InvalidInputDataException(String message, Map<String, List<String>> errors) {
        super(message);
        this.errors = errors;
    }

    public InvalidInputDataException(Map<String, List<String>> errors) {
        super("Validation failed"); // Общее сообщение
        this.errors = errors;
    }
}