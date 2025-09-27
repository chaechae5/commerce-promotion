package com.chae.promo.order.event;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;


import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@RequiredArgsConstructor
@Service
public class OrderPlacedHandlerService {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_DELAY = 100L;
    private static final long MAX_BACKOFF_DELAY = 1000L;

    private final OrderStockProcessor orderStockProcessor;


    public void processProductStockChange(OrderPlacedEvent event) {

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {

            try {

                orderStockProcessor.processStockDecrease(event);

                return; // 성공 시 메서드 종료

            } catch (CommonCustomException e) {
                log.warn("비즈니스 로직 검증 실패 - eventId: {}, error: {}", event.getEventId(), e.getMessage());
                throw e; //재시도 제외 예외

            } catch (DataIntegrityViolationException e) {
                // 재시도할 필요 없는 예외는 DLT로 바로 전송
                log.warn("Audit 저장 실패 (데이터 무결성 위반). eventId: {}, userId: {}, e: {}",
                        event.getEventId(), event.getUserId(), e.getMessage());

                throw new CommonCustomException(CommonErrorCode.PRODUCT_STOCK_AUDIT_SAVE_FAILED);

            }  catch (OptimisticLockingFailureException e) {
                log.warn("재고 업데이트 실패 - OptimisticLockException 발생. eventId: {}, userId: {}, attempt: {}/{}",
                        event.getEventId(), event.getUserId(), attempt + 1, MAX_RETRIES);

                if (attempt >= MAX_RETRIES -1) {
                    handleFinalFailure(e, event);
                }

                applyBackoff(attempt);


            } catch (Exception e) {
                log.error("DB 저장 실패로 인한 재처리 요청. eventId: {}, attempt: {}/{}", event.getEventId(), attempt + 1, MAX_RETRIES, e);

                if (attempt >= MAX_RETRIES -1) {
                    handleFinalFailure(e, event);
                }

               applyBackoff(attempt);
            }
        }
    }

    private void handleFinalFailure(Exception e, OrderPlacedEvent event) {
        log.error("최대 재시도 초과 - eventId: {}", event.getEventId(), e);
        throw (e instanceof RuntimeException re) ? re : new RuntimeException(e); // 재시도 실패 시 예외를 던져 DLT로 전송되도록 함
    }

    private void applyBackoff(int attemptNumber) {
        try {

            // Exponential backoff: 100ms, 200ms, 400ms...
            long delay = Math.min(
                    (long) Math.pow(2, attemptNumber) * INITIAL_BACKOFF_DELAY,
                    MAX_BACKOFF_DELAY
            );

            // 지터(jitter) 추가 - ±20% 랜덤 변동으로 thundering herd 방지
            double jitterFactor = 0.8 + (ThreadLocalRandom.current().nextDouble() * 0.4);
            delay = (long) (delay * jitterFactor);
            delay = Math.max(delay, INITIAL_BACKOFF_DELAY); // 최소값 보장

            log.info("백오프 적용 중... delay: {}ms", delay);
            Thread.sleep(delay);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("백오프 중 인터럽트 발생", e);
            throw new RuntimeException("백오프 중 인터럽트 발생", e);
        }
    }


}
