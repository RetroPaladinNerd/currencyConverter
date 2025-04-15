package com.example.currencyconverter.aspect;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // Определяем Pointcut для всех методов во всех классах пакета controller
    @Pointcut("execution(* com.example.currencyconverter.controller..*(..))")
    public void controllerMethods() {}

    // Определяем Pointcut для всех методов во всех классах пакета service
    @Pointcut("execution(* com.example.currencyconverter.service..*(..))")
    public void serviceMethods() {}

    // Логирование до вызова метода контроллера
    @Before("controllerMethods()")
    public void logBeforeController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.debug("CONTROLLER CALL: {}() with arguments: {}", methodName, Arrays.toString(args));
    }

    // Логирование после успешного выполнения метода контроллера
    @AfterReturning(pointcut = "controllerMethods()", returning = "result")
    public void logAfterReturningController(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().toShortString();
        // Осторожно логируем результат, он может быть большим
        String resultString = (result != null) ? result.toString() : "null";
        if (resultString.length() > 1000) { // Ограничим длину вывода результата
            resultString = resultString.substring(0, 1000) + "... (truncated)";
        }
        log.debug("CONTROLLER RETURN: {}() returned: {}", methodName, resultString);
    }

    // Логирование исключения, выброшенного из метода контроллера или сервиса
    @AfterThrowing(pointcut = "controllerMethods() || serviceMethods()", throwing = "exception")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().toShortString();
        log.error("EXCEPTION in {}: {}() with cause = '{}'",
                joinPoint.getSignature().getDeclaringTypeName(),
                methodName,
                exception.getCause() != null ? exception.getCause() : "NULL",
                exception); // Логируем полный стектрейс
    }

    // Можно добавить логирование времени выполнения для сервисных методов (опционально)
    @Around("serviceMethods()")
    public Object logServiceExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed(); // Выполняем метод
        long endTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        log.trace("SERVICE TIME: {}() executed in {} ms", methodName, (endTime - startTime));
        return result;
    }
}