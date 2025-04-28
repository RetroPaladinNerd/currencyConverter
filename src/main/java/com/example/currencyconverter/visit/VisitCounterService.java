package com.example.currencyconverter.visit;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VisitCounterService {

    private final AtomicLong visitCount = new AtomicLong(0);


    public void increment() {
        visitCount.incrementAndGet();
    }


    public long getCount() {
        return visitCount.get();
    }


    public void reset() {
        visitCount.set(0);
    }
}