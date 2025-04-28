package com.example.currencyconverter.config;

import com.example.currencyconverter.visit.VisitCounterInterceptor; // <-- Импорт
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; // <-- Добавить Lombok
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor // <-- Используем Lombok для конструктора
public class AppConfig implements WebMvcConfigurer {

    private final CacheConfig cacheConfig;
    private final VisitCounterInterceptor visitCounterInterceptor; // <-- Инжектируем интерцептор счетчика

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Интерцептор для времени выполнения и размера кэша
        registry.addInterceptor(new TimeExecutionInterceptor(cacheConfig))
                .addPathPatterns("/**") // Применяем ко всем путям
                .excludePathPatterns("/api/exchange-rates/cache/size", "/api/visits/count", "/api/logs/**", "/swagger-ui/**", "/v3/api-docs/**"); // Исключаем эндпоинты, где он не нужен или может мешать, также swagger

        // Интерцептор для счетчика посещений
        registry.addInterceptor(visitCounterInterceptor)
                .addPathPatterns("/**") // <-- ИЗМЕНЕНО: Применяем ко ВСЕМ путям
                .excludePathPatterns("/api/visits/count", "/swagger-ui/**", "/v3/api-docs/**", "/error"); // <-- ИЗМЕНЕНО: Исключаем эндпоинт получения счетчика, swagger и страницу ошибок по умолчанию
        // Примечание: Если у вас есть статические ресурсы (CSS, JS, images), их пути тоже стоит сюда добавить, например "/css/**", "/js/**" и т.д.
    }

    // Класс TimeExecutionInterceptor остается без изменений
    public static class TimeExecutionInterceptor implements HandlerInterceptor {
        private static final String START_TIME_ATTRIBUTE = "startTime";
        private final CacheConfig cacheConfig;

        public TimeExecutionInterceptor(CacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            // Убрали проверку на GET, чтобы считать время выполнения для всех методов
            long startTime = System.currentTimeMillis();
            request.setAttribute(START_TIME_ATTRIBUTE, startTime);
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
            // Проверяем, что атрибут был установлен в preHandle
            if (request.getAttribute(START_TIME_ATTRIBUTE) != null) {
                long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                response.addHeader("X-Execution-Time", String.valueOf(executionTime));
                // Используем актуальный метод для получения размера кэша, если он реализован
                response.addHeader("X-Cache-Size-KB", String.valueOf(cacheConfig.getCacheSizeInKB()));
            }
        }
    }
}