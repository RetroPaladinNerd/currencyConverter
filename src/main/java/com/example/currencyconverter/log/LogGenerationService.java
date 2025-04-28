package com.example.currencyconverter.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LogGenerationService {

    // Потокобезопасное хранилище для статусов задач
    private final Map<String, LogJob> jobStatuses = new ConcurrentHashMap<>();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    // Метод запускает генерацию асинхронно
    public String startLogGeneration() {
        String jobId = UUID.randomUUID().toString();
        LogJob job = new LogJob(jobId);
        jobStatuses.put(jobId, job);
        log.info("Starting log generation job with ID: {}", jobId);
        generateLogFileAsync(job); // Вызов асинхронного метода
        return jobId;
    }

    // Асинхронный метод генерации файла
    @Async // Указывает Spring выполнить этот метод в отдельном потоке
    public CompletableFuture<Void> generateLogFileAsync(LogJob job) {
        job.setStatus(LogJobStatus.RUNNING);
        log.info("Job {} started execution.", job.getJobId());

        Path tempFile = null;
        try {
            // Создаем временный файл
            tempFile = Files.createTempFile("app-log-" + job.getJobId() + "-", ".log");
            log.debug("Created temporary log file: {}", tempFile);

            // Симуляция длительной операции записи в лог
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardOpenOption.APPEND)) {
                for (int i = 1; i <= 10; i++) { // Увеличить для более долгой генерации
                    writer.write(LocalDateTime.now().format(formatter) + " - Log entry " + i + " for job " + job.getJobId());
                    writer.newLine();
                    log.trace("Writing line {} for job {}", i, job.getJobId());
                    Thread.sleep(1);
                }
            }

            job.setFilePath(tempFile);
            job.setStatus(LogJobStatus.COMPLETED);
            log.info("Job {} completed successfully. File generated at: {}", job.getJobId(), tempFile);

        } catch (IOException e) {
            log.error("Job {} failed during file I/O operation.", job.getJobId(), e);
            job.setErrorMessage("Failed to write log file: " + e.getMessage());
            job.setStatus(LogJobStatus.FAILED);
            // Попытаться удалить временный файл, если он был создан
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.warn("Could not delete temporary file {} after failure for job {}", tempFile, job.getJobId(), ex);
                }
            }
        } catch (InterruptedException e) {
            log.warn("Job {} was interrupted.", job.getJobId(), e);
            job.setErrorMessage("Log generation was interrupted.");
            job.setStatus(LogJobStatus.FAILED);
            Thread.currentThread().interrupt(); // Восстановить статус прерывания
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.warn("Could not delete temporary file {} after interruption for job {}", tempFile, job.getJobId(), ex);
                }
            }
        } catch (Exception e) { // Общий обработчик на всякий случай
            log.error("Job {} failed with an unexpected error.", job.getJobId(), e);
            job.setErrorMessage("An unexpected error occurred: " + e.getMessage());
            job.setStatus(LogJobStatus.FAILED);
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.warn("Could not delete temporary file {} after unexpected error for job {}", tempFile, job.getJobId(), ex);
                }
            }
        }
        // CompletableFuture нужен, если бы мы хотели дождаться завершения,
        // но здесь мы просто обновляем статус в карте.
        return CompletableFuture.completedFuture(null);
    }

    // Получение статуса задачи
    public Optional<LogJob> getJob(String jobId) {
        return Optional.ofNullable(jobStatuses.get(jobId));
    }
}