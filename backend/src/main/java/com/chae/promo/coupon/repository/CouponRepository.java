package com.chae.promo.coupon.repository;

import com.chae.promo.coupon.entity.Coupon;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    Optional<Coupon> findByPublicId(String publicId);
    List<Coupon> findByPublicIdIn(Set<String> publicIds);

    default Coupon getCouponByPublicIdOrThrow(String publicId) {
        return findByPublicId(publicId)
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.COUPON_NOT_FOUND));
    }
}
