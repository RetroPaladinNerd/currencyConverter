package com.example.currencyconverter.controller;

import com.example.currencyconverter.dto.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Log Management", description = "Endpoints for accessing application logs (Administrative).")
public class LogController {

    @Value("${logging.file.name}")
    private String logFilePath;

    private static final DateTimeFormatter LOG_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/{date}")
    @Operation(summary = "Download logs by date", description = "Downloads a log file containing entries for the specified date.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Log file ready for download",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid date format supplied (must be YYYY-MM-DD)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Log file not found or no entries for the specified date",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error reading log file",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Resource> getLogsByDate(
            @Parameter(description = "Date for which to retrieve logs (format: YYYY-MM-DD)", required = true, example = "2025-04-07")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Path path = Paths.get(logFilePath);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.error("Log file not found or not readable at path: {}", logFilePath);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log file not found or cannot be read: " + path.getFileName());
        }

        String dateString = date.format(LOG_DATE_FORMATTER);
        log.info("Requesting logs for date: {}", dateString);

        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            String filteredLogContent = lines
                    .filter(line -> line.length() >= dateString.length() && line.startsWith(dateString))
                    .collect(Collectors.joining(System.lineSeparator()));

            if (filteredLogContent.isEmpty()) {
                log.warn("No log entries found for date: {}", dateString);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No log entries found for date: " + dateString);
            }

            InputStream inputStream = new ByteArrayInputStream(filteredLogContent.getBytes(StandardCharsets.UTF_8));
            Resource resource = new InputStreamResource(inputStream);
            HttpHeaders headers = createHeaders(dateString);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(resource);

        } catch (IOException e) {
            log.error("Error reading log file: {}", logFilePath, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading log file", e);
        }
    }

    private HttpHeaders createHeaders(String dateString) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs_" + dateString + ".log");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");
        return headers;
    }
}