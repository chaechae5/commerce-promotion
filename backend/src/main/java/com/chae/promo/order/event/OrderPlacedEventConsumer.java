package com.chae.promo.order.event;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.exception.CommonCustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedEventConsumer {

    private final OrderPlacedHandlerService handlerService;

    @RetryableTopic(
            attempts = "4", // 총 4번 시도 (최초 1번 + 재시도 3번)
            backoff = @Backoff(delay = 1000, multiplier = 2), // 1초, 2초, 4초 간격으로 재시도
            dltStrategy = DltStrategy.FAIL_ON_ERROR, // DLQ로 보내는 것조차 실패하면 에러를 남기고 중지
            autoCreateTopics = "false", // 자동으로 토픽을 생성하지 않음
            exclude = { // 재시도 제외 exception
                    CommonCustomException.class,
                    OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class
            }
    )
    @KafkaListener(topics = TopicNames.ORDER_PLACED, groupId = "order.group")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("구매 요청 이벤트 수신: {}", event);

        // 처리 로직
        handlerService.processProductStockChange(event);
    }

    @DltHandler
    public void handleDlt(OrderPlacedEvent event,
                          @Header(KafkaHeaders.EXCEPTION_FQCN) String exceptionClassName,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {

        // 재시도가 의미 없는 예외인지 확인
        boolean isBusinessException = exceptionClassName.equals(OptimisticLockingFailureException.class.getName()) ||
                exceptionClassName.equals(CommonCustomException.class.getName()) ||
                exceptionClassName.equals(DataIntegrityViolationException.class.getName());

        if (isBusinessException) {
            //  비즈니스 예외: 에러 로그만 기록
            log.warn("""
                            [DLT] 비즈니스 예외 처리 실패.
                            - Event ID: {}
                            - Order Public ID: {}
                            - Error: {}
                            """,
                    event.getEventId(), event.getOrderPublicId(), errorMessage);
        } else {
            // 시스템 장애: 에러 로그 기록 + 알림 발송
            log.error("""
                            [DLT] 시스템 장애 처리 최종 실패!
                            - Event ID: {}
                            - Order Public ID: {}
                            - Exception Type: {}
                            - Error Message: {}
                            """,
                    event.getEventId(), event.getOrderPublicId(), exceptionClassName, errorMessage);

            // TODO: 외부 알림 서비스 호출
        }
    }
}