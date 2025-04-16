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
    @Pointcut("execution(* com.example.currencyconverter.controller..*(..))")
    public void controllerMethods() {}
    @Pointcut("execution(* com.example.currencyconverter.service..*(..))")
    public void serviceMethods() {}
    @Before("controllerMethods()")
    public void logBeforeController(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.debug("CONTROLLER CALL: {}() with arguments: {}", methodName, Arrays.toString(args));
    }
    @AfterReturning(pointcut = "controllerMethods()", returning = "result")
    public void logAfterReturningController(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().toShortString();
        String resultString = (result != null) ? result.toString() : "null";
        if (resultString.length() > 1000) {
            resultString = resultString.substring(0, 1000) + "... (truncated)";
        }
        log.debug("CONTROLLER RETURN: {}() returned: {}", methodName, resultString);
    }
    @AfterThrowing(pointcut = "controllerMethods() || serviceMethods()", throwing = "exception")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().toShortString();
        log.error("EXCEPTION in {}: {}() with cause = '{}'",
                joinPoint.getSignature().getDeclaringTypeName(),
                methodName,
                exception.getCause() != null ? exception.getCause() : "NULL",
                exception);
    }
    @Around("serviceMethods()")
    public Object logServiceExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long endTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        log.trace("SERVICE TIME: {}() executed in {} ms", methodName, (endTime - startTime));
        return result;
    }
}