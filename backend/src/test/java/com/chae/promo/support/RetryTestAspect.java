package com.chae.promo.support;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@TestComponent
public class RetryTestAspect {

    private final AtomicInteger totalCallCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final Map<String, Integer> methodCallCounts = new ConcurrentHashMap<>();

    @Around("@annotation(retryable)")
    public Object trackRetryableCalls(ProceedingJoinPoint joinPoint, Retryable retryable) throws Throwable {
        String methodKey = getMethodKey(joinPoint);

        totalCallCount.incrementAndGet();
        methodCallCounts.merge(methodKey, 1, Integer::sum);

        try {
            Object result = joinPoint.proceed();

            RetryContext ctx = RetrySynchronizationManager.getContext();
            if (ctx != null && ctx.getRetryCount() > 0) {
                int attempts = ctx.getRetryCount() + 1; // 첫 시도 포함
                System.out.println("✅ " + methodKey + " - 재시도 후 성공 (시도: " + attempts + ")");
            }
            return result;

        } catch (Exception ex) {
            retryCount.incrementAndGet();
            RetryContext ctx = RetrySynchronizationManager.getContext();
            Integer attempt = (ctx != null ? ctx.getRetryCount() + 1 : null);

            System.out.println("⚠️ " + methodKey +
                    (attempt != null ? " - 시도 #" + attempt : "") +
                    " 실패: " + ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String getMethodKey(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName() + "." + joinPoint.getSignature().getName();
    }

    public int getTotalCallCount() {
        return totalCallCount.get();
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public int getCallCountForMethod(String methodName) {
        String key = methodCallCounts.keySet().stream()
                .filter(k -> k.endsWith("." + methodName))
                .findFirst()
                .orElse(methodName);
        return methodCallCounts.getOrDefault(key, 0);
    }

    public void reset() {
        totalCallCount.set(0);
        retryCount.set(0);
        methodCallCounts.clear();
    }

    public void printStatistics() {
        System.out.println("\n=== Retry Statistics ===");
        System.out.println("전체 메서드 호출 수: " + totalCallCount.get());
        System.out.println("재시도 횟수: " + retryCount.get());
        System.out.println("메서드별 호출 상세:");
        methodCallCounts.forEach((method, count) ->
                System.out.println("  " + method + ": " + count + " 회 호출"));
        System.out.println("========================\n");
    }
}
