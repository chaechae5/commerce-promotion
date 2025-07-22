package com.chae.promo.coupon.controller;

import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.service.CouponServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "쿠폰 api")
public class CouponController {

    private final CouponServiceImpl couponService;

    @PostMapping("/issue")
    @Operation(summary = "쿠폰 발급")
    public ResponseEntity<CouponResponse.Issue> issueCoupon(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(couponService.issueCoupon(userDetails.getUsername()));
    }

}
