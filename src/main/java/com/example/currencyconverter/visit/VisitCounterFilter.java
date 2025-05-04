package com.example.currencyconverter.visit;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class VisitCounterFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(VisitCounterFilter.class);
    private final VisitCounterService visitCounterService;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();


    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/api/visits/count/total",
            "/api/visits/count/total/",
            "/api/visits/count",
            "/api/visits/count/",
            "/api/visits/counts",
            "/api/visits/counts/",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/error",
            "/favicon.ico"

    );

    public VisitCounterFilter(VisitCounterService visitCounterService) {
        this.visitCounterService = visitCounterService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestUri = httpRequest.getRequestURI();

        boolean excluded = false;
        for (String pattern : EXCLUDED_PATHS) {
            if (pathMatcher.match(pattern, requestUri)) {
                excluded = true;
                log.trace("VisitCounterFilter: Skipping excluded URI: {}", requestUri);
                break;
            }
        }

        if (!excluded) {
            log.info("VisitCounterFilter: Incrementing visit count for URI: {}", requestUri);
            try {
                visitCounterService.incrementVisit(requestUri);
            } catch (Exception e) {

                log.error("VisitCounterFilter: Error incrementing visit count for URI: {}", requestUri, e);
            }
        }

        chain.doFilter(request, response);
    }

}