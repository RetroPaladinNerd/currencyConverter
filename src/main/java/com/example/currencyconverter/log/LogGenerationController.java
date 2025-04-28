package com.example.currencyconverter.log;

import com.example.currencyconverter.dto.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Log Generation", description = "Endpoints for asynchronous log file generation.")
public class LogGenerationController {

    private final LogGenerationService logGenerationService;
    private static final long FORCED_PENDING_DURATION_SECONDS = 20;

    @PostMapping("/generate")
    @Operation(summary = "Start log file generation", description = "Initiates an asynchronous process to generate a log file.")
    @ApiResponses(value = {
            // ... ApiResponses ...
            @ApiResponse(responseCode = "202", description = "Log generation process accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LogJobResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during job initiation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<LogJobResponseDto> generateLog() {
        try {
            String jobId = logGenerationService.startLogGeneration();
            LogJobResponseDto response = new LogJobResponseDto(jobId, "Log generation process started.");
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            log.error("Failed to initiate log generation", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start log generation", e);
        }
    }

    @GetMapping("/{jobId}/status")
    @Operation(summary = "Get log generation status", description = "Retrieves the status of a log job. Shows PENDING for the first 15 seconds if the job completes early.")
    @ApiResponses(value = {
            // ... ApiResponses ...
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LogJobStatusDto.class))),
            @ApiResponse(responseCode = "404", description = "Job ID not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<LogJobStatusDto> getJobStatus(
            @Parameter(description = "ID of the log generation job", required = true, example = "a1b2c3d4-...")
            @PathVariable String jobId) {

        return logGenerationService.getJob(jobId)
                .map(job -> {
                    LogJobStatus currentActualStatus = job.getStatus();
                    LogJobStatus statusToSend = currentActualStatus;

                    if (currentActualStatus == LogJobStatus.COMPLETED) {
                        LocalDateTime now = LocalDateTime.now();
                        Duration durationSinceStart = Duration.between(job.getStartTime(), now);
                        if (durationSinceStart.getSeconds() < FORCED_PENDING_DURATION_SECONDS) {
                            statusToSend = LogJobStatus.PENDING;
                            log.trace("Job {} is actually COMPLETED but showing PENDING due to < {}s rule.", jobId, FORCED_PENDING_DURATION_SECONDS);
                        }
                    }

                    LogJobStatusDto dto = new LogJobStatusDto(
                            job.getJobId(),
                            statusToSend,
                            job.getFilePath() != null ? job.getFilePath().toString() : null,
                            job.getErrorMessage()
                    );
                    return ResponseEntity.ok(dto);
                })
                .orElseThrow(() -> {
                    log.warn("Log job status requested for unknown ID: {}", jobId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Log job not found with ID: " + jobId);
                });
    }

    @GetMapping("/{jobId}/file")
    @Operation(summary = "Download generated log file", description = "Downloads the log file generated by a completed job. Fails if the job status appears as PENDING (due to 15s rule).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Log file download initiated",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
            @ApiResponse(responseCode = "404", description = "Job ID not found or job failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Job is running or appears as PENDING (within 20s window)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Error reading the generated file or file not found on disk",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Resource> downloadLogFile(
            @Parameter(description = "ID of the completed log generation job", required = true, example = "a1b2c3d4-...")
            @PathVariable String jobId) {


        LogJob job = logGenerationService.getJob(jobId)
                .orElseThrow(() -> {
                    log.warn("Log file download requested for unknown ID: {}", jobId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Log job not found with ID: " + jobId);
                });


        LogJobStatus currentActualStatus = job.getStatus();
        LogJobStatus apparentStatus = currentActualStatus;

        if (currentActualStatus == LogJobStatus.COMPLETED) {
            LocalDateTime now = LocalDateTime.now();
            Duration durationSinceStart = Duration.between(job.getStartTime(), now);
            if (durationSinceStart.getSeconds() < FORCED_PENDING_DURATION_SECONDS) {
                apparentStatus = LogJobStatus.PENDING;
            }
        }


        if (apparentStatus == LogJobStatus.COMPLETED) {

            Path filePath = job.getFilePath();
            if (filePath == null || !Files.exists(filePath)) {
                log.error("Log file path is missing or file does not exist for completed job ID: {}", jobId);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Generated log file not found on disk for job ID: " + jobId);
            }

            try {
                InputStream inputStream = Files.newInputStream(filePath);
                Resource resource = new InputStreamResource(inputStream);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName().toString() + "\"");
                headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
                headers.add(HttpHeaders.PRAGMA, "no-cache");
                headers.add(HttpHeaders.EXPIRES, "0");

                return ResponseEntity.ok()
                        .headers(headers)
                        .contentLength(Files.size(filePath))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);

            } catch (IOException e) {
                log.error("Error reading log file {} for job ID: {}", filePath, jobId, e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading generated log file", e);
            }
        } else if (apparentStatus == LogJobStatus.FAILED) {

            log.info("Attempted to download file for failed job ID: {}", jobId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log generation failed: " + job.getErrorMessage()); // 404 Not Found т.к. ресурса (файла) нет
        } else {

            log.info("Attempted to download file for job ID {} which appears as {}", jobId, apparentStatus);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Log generation is not yet complete (status: " + apparentStatus + ")"); // 409 Conflict - ресурс еще не готов
        }
    }
}