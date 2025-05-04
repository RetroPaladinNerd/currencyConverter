package com.example.currencyconverter.visit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VisitCounterService {

    private static final Logger log = LoggerFactory.getLogger(VisitCounterService.class);

    private final ConcurrentHashMap<String, AtomicLong> visitCounts = new ConcurrentHashMap<>();

    public void incrementVisit(String requestUri) {
        if (requestUri == null) {
            requestUri = "UNKNOWN_URI";
        }
        visitCounts.computeIfAbsent(requestUri, k -> {
            log.debug("Creating new counter for URI: {}", k);
            return new AtomicLong(0);
        }).incrementAndGet();
        log.trace("Incremented count for URI: {}", requestUri);
    }

    public long getTotalVisitCount() {
        return visitCounts.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

}