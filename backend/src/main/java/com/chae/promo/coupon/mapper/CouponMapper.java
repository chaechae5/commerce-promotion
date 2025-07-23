package com.chae.promo.coupon.mapper;

import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.coupon.entity.CouponIssue;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CouponMapper {

    // CouponIssue -> DTO 변환

    /**
     * CouponIssue 엔티티 리스트를 CouponResponse.Info DTO 리스트로 변환
     *
     * @param couponIssues 변환할 CouponIssue 엔티티 리스트
     * @return 변환된 CouponResponse.Info DTO 리스트
     */
    public List<CouponResponse.Info> toInfoListFromCouponIssues(List<CouponIssue> couponIssues) {
        if (couponIssues == null || couponIssues.isEmpty()) {
            return Collections.emptyList();
        }
        return couponIssues.stream()
                .map(this::convertCouponIssueToInfo)
                .collect(Collectors.toList());
    }

    /**
     * CouponIssue 엔티티를 CouponResponse.Info DTO로 변환하는 내부 로직
     */
    private CouponResponse.Info convertCouponIssueToInfo(CouponIssue couponIssue) {
        return convertCouponToInfo(couponIssue.getCoupon());
    }


    // Coupon -> DTO 변환

    /**
     * Coupon 엔티티를 CouponResponse.Info DTO로 변환하여 Optional로 감싸 반환
     *
     * @param coupon 변환할 Coupon 엔티티
     * @return 변환된 DTO가 담긴 Optional 객체. coupon이 null이면 Optional.empty() 반환.
     */
    public Optional<CouponResponse.Info> toInfoOptional(Coupon coupon) {
        return Optional.ofNullable(coupon).map(this::convertCouponToInfo);
    }

    /**
     * Coupon 엔티티 리스트를 CouponResponse.Info DTO 리스트로 변환
     *
     * @param coupons 변환할 Coupon 엔티티 리스트
     * @return 변환된 CouponResponse.Info DTO 리스트
     */
    public List<CouponResponse.Info> toInfoListFromCoupons(List<Coupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return Collections.emptyList();
        }
        return coupons.stream()
                .map(this::convertCouponToInfo)
                .collect(Collectors.toList());
    }

    /**
     * Coupon 엔티티를 DTO로 변환하는 핵심 로직입니다.
     * 모든 변환 메서드가 이 메서드를 재사용합니다.
     */
    private CouponResponse.Info convertCouponToInfo(Coupon coupon) {
        return CouponResponse.Info.builder()
                .couponId(coupon.getPublicId())
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .startDate(coupon.getStartDate())
                .endDate(coupon.getEndDate())
                .expireDate(coupon.getExpireDate())
                .validDays(coupon.getValidDays())
                .build();
    }
}
