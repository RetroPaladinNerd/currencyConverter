package com.example.currencyconverter.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final CacheConfig cacheConfig;

    public AppConfig(CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TimeExecutionInterceptor(cacheConfig)).addPathPatterns("/**").excludePathPatterns("/exchange-rates/cache/size");
    }

    public static class TimeExecutionInterceptor implements HandlerInterceptor {

        private final CacheConfig cacheConfig;

        public TimeExecutionInterceptor(CacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (request.getMethod().equalsIgnoreCase("GET")) {
                long startTime = System.currentTimeMillis();
                request.setAttribute("startTime", startTime);
            }
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
            if (request.getAttribute("startTime") != null) {
                long startTime = (Long) request.getAttribute("startTime");
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                response.addHeader("X-Execution-Time", String.valueOf(executionTime));
                response.addHeader("X-Cache-Size-KB", String.valueOf(cacheConfig.getCacheSizeInKB()));
            }
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        }
    }
}