package com.example.currencyconverter.log;

import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.Getter;

@Data
public class LogJob {
    private final String jobId;
    private volatile LogJobStatus status = LogJobStatus.PENDING;
    private volatile Path filePath;
    private volatile String errorMessage;


    @Getter
    private final LocalDateTime startTime;

    public LogJob(String jobId) {
        this.jobId = jobId;
        this.startTime = LocalDateTime.now();
    }

}