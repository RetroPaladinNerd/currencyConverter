package com.example.currencyconverter.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AppConfig implements WebMvcConfigurer {

    private final CacheConfig cacheConfig;
    // УДАЛИТЕ ПОЛЕ: private final VisitCounterInterceptor visitCounterInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Интерцептор для времени выполнения и размера кэша (остается как был)
        registry.addInterceptor(new TimeExecutionInterceptor(cacheConfig))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/exchange-rates/cache/size",
                        "/api/visits/count/total",
                        "/api/visits/count/total/",
                        "/api/logs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/error",
                        "/favicon.ico"
                );

    }

    // Класс TimeExecutionInterceptor (остается как был)
    public static class TimeExecutionInterceptor implements HandlerInterceptor {
        private static final String START_TIME_ATTRIBUTE = "startTime";
        private final CacheConfig cacheConfig;

        public TimeExecutionInterceptor(CacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            long startTime = System.currentTimeMillis();
            request.setAttribute(START_TIME_ATTRIBUTE, startTime);
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
            if (request.getAttribute(START_TIME_ATTRIBUTE) != null) {
                long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                response.addHeader("X-Execution-Time", String.valueOf(executionTime));
                response.addHeader("X-Cache-Size-KB", String.valueOf(cacheConfig.getCacheSizeInKB()));
            }
        }
    }
}