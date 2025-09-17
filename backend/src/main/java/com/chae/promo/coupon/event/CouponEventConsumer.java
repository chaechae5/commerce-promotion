package com.chae.promo.coupon.event;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.coupon.service.CouponIssueHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

//// 역할: Kafka 메시지를 수신하고, 실제 처리는 HandlerService에 위임
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponEventConsumer {

    private final CouponIssueHandlerService handlerService;

    /**
     * 쿠폰 발급 이벤트를 처리하는 메서드
     * - Kafka에서 쿠폰 발급 이벤트를 수신하고, HandlerService에 위임하여 처리
     * - 재시도 및 DLQ 설정을 통해 안정적인 메시지 처리를 보장
     *
     * @param event 쿠폰 발급 이벤트
     */
    @RetryableTopic(
            attempts = "4", // 총 4번 시도 (최초 1번 + 재시도 3번)
            backoff = @Backoff(delay = 1000, multiplier = 2), // 1초, 2초, 4초 간격으로 재시도
            dltStrategy = DltStrategy.FAIL_ON_ERROR, // DLQ로 보내는 것조차 실패하면 에러를 남기고 중지
            autoCreateTopics = "false" // 자동으로 토픽을 생성하지 않음
    )
    @KafkaListener(topics = TopicNames.COUPON_ISSUED, groupId = "coupon.group")
    public void handleCouponIssued(CouponIssuedEvent event) {
        log.info("쿠폰 발급 이벤트 수신: {}", event);

        // 처리 로직
        handlerService.saveCouponIssueFromEvent(event);
    }
}