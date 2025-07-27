package com.chae.promo.coupon.service;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.event.CouponIssuedEvent;
import com.chae.promo.coupon.repository.CouponRepository;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


// 역할: 쿠폰 발급 이벤트 비즈니스 로직 처리
@Slf4j
@RequiredArgsConstructor
@Service
public class CouponIssueHandlerService {

    private final CouponRepository couponRepository;

    private final CouponIssuePersistenceService couponIssuePersistenceService;

    @Transactional
    public void saveCouponIssueFromEvent(CouponIssuedEvent event){
        String userId = event.getUserId();
        String couponPublicId = event.getCouponPublicId();
        String couponIssueId = event.getCouponIssueId();
        Coupon coupon = null;
        try {
            coupon = findCouponByPublicId(event.getCouponPublicId());

            //DB에 저장 - couponIssuePersistenceService로 위임
            couponIssuePersistenceService.saveCouponIssue(coupon,
                    userId,
                    event.getExpiredAt(),
                    event.getCouponIssueId());

            log.info("DB 저장 성공. couponPublicId: {}, couponCode: {}, userId: {}, couponIssueId: {}",
                    coupon.getPublicId(), coupon.getCode(), userId, event.getCouponIssueId());

        } catch (Exception e) {
            String couponCode = (coupon != null) ? coupon.getCode() : "N/A";

            log.error("쿠폰 발급 DB 저장 실패. event:{}, userId: {}, couponPublicId: {}, couponCode:{}, couponIssueId: {}",
                    event,
                    userId,
                    couponPublicId,
                    couponCode,
                    couponIssueId,
                    e);

            // Spring Kafka의 재시도(Retry) 및 DLQ(Dead Letter Queue) 메커니즘을 활성화하는 역할
            // 따라서, 이 예외가 발생하면 Kafka는 해당 메시지를 재처리하려고 시도
            throw new RuntimeException("DB 저장 실패로 인한 재처리 요청", e);
        }
    }

    private Coupon findCouponByPublicId(String publicId) {
        return couponRepository.findByPublicId(publicId)
                .orElseThrow(() -> {
                    log.warn("쿠폰 조회 실패 couponPublicId: {}", publicId);
                    return new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND);
                });
    }

}
