package com.chae.promo.coupon.event;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.coupon.service.redis.CouponRedisKeyManager;
import com.chae.promo.coupon.service.redis.CouponRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 역할: 쿠폰 발급 실패 이벤트를 수신하고, 보상 트랜잭션을 수행
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponDLQConsumer {


    private final CouponRedisService couponRedisService;
    private final CouponRedisKeyManager couponRedisKeyManager;

    /**
     * 쿠폰 발급 실패 이벤트를 수신하여 보상 트랜잭션을 수행
     * 이 메서드는 쿠폰 발급이 최종적으로 실패했을 때 호출
     * .dlt는 Dead Letter Topic을 의미하는 기본 접미사
     *
     * @param failedEvent 발급 실패 이벤트
     */
    @KafkaListener(topics = TopicNames.COUPON_ISSUED + ".dlt", groupId = "coupon.dlt.group")
    public void handleIssuanceFailure(CouponIssuedEvent failedEvent) {
        log.error("쿠폰 발급 최종 실패! 보상 트랜잭션을 시작합니다. event: {}", failedEvent);

        String couponStockKey = couponRedisKeyManager.getCouponStockKey(failedEvent.getCouponPublicId(), failedEvent.getCouponCode());
        String userCouponSetKey = couponRedisKeyManager.getUserCouponSetKey(failedEvent.getUserId());
        String couponIssuedUserSetKey = couponRedisKeyManager.getCouponIssuedUserSetKey(failedEvent.getCouponPublicId(), failedEvent.getCouponCode());

        // 보상 트랜잭션: Redis 재고 롤백
        couponRedisService.rollbackRedisCouponStock(couponStockKey,
                userCouponSetKey,
                couponIssuedUserSetKey,
                failedEvent.getUserId(),
                failedEvent.getCouponPublicId()
        );

        // todo. 사용자에게 실패 알림

    }
}
