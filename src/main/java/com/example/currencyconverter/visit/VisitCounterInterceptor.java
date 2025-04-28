package com.example.currencyconverter.visit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisitCounterInterceptor implements HandlerInterceptor {

    private final VisitCounterService visitCounterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        visitCounterService.increment();
        log.trace("Incremented visit count for request URI: {}", request.getRequestURI());
        return true;
    }

}