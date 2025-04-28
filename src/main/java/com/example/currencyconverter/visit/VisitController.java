package com.example.currencyconverter.visit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
@Tag(name = "Visit Tracking", description = "Endpoints for tracking website visits.")
public class VisitController {

    private final VisitCounterService visitCounterService;

    // Эндпоинт для получения текущего счетчика
    @GetMapping("/count")
    @Operation(summary = "Get visit count", description = "Retrieves the total number of tracked visits.")
    @ApiResponse(responseCode = "200", description = "Count retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "integer", format = "int64")))
    public ResponseEntity<Long> getVisitCount() {
        return ResponseEntity.ok(visitCounterService.getCount());
    }
}