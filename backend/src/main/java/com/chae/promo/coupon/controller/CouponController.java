package com.chae.promo.coupon.controller;

import com.chae.promo.coupon.dto.CouponResponse;
import com.chae.promo.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "쿠폰 api")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/issue")
    @Operation(summary = "쿠폰 발급")
    public ResponseEntity<CouponResponse.Issue> issueCoupon(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(couponService.issueCoupon(userDetails.getUsername()));
    }

    @GetMapping
    @Operation(summary = "전체 쿠폰 조회")
    public ResponseEntity<List<CouponResponse.Info>> getAll(
    ) {
        return ResponseEntity.ok(couponService.getAll());
    }

    @GetMapping("/my")
    @Operation(summary = "내 쿠폰 조회")
    public ResponseEntity<List<CouponResponse.Info>> getMyCoupons(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(couponService.getMyCoupons(userDetails.getUsername()));
    }

}
