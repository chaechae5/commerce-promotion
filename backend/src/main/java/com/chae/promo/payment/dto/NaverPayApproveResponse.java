package com.chae.promo.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 네이버페이 전체 응답
public record NaverPayApproveResponse(
        String code,
        String message,
        NaverPayApproveResponse.Body body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String paymentId,
            NaverPayApproveResponse.Detail detail
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            String paymentId,
            String payHistId,
            String merchantName,
            String merchantId,
            String merchantPayKey,
            String merchantUserKey,
            String admissionTypeCode,   // 01 원승인, 03 전체취소, 04 부분취소
            String admissionYmdt,       // YYYYMMDDHHmmss
            String tradeConfirmYmdt,    // YYYYMMDDHH24MMSS 정산 기준 완료시각(또는 결제일시)
            String admissionState,      // SUCCESS / FAIL
            long totalPayAmount,
            long applyPayAmount,
            long primaryPayAmount,
            long npointPayAmount,
            long giftCardAmount,
            long discountPayAmount,
            long taxScopeAmount,
            long taxExScopeAmount,
            long environmentDepositAmount,
            String primaryPayMeans,     // CARD / BANK / (없고 npointPayAmount>0 면 포인트)
            String cardCorpCode,
            String cardNo,              // 마스킹
            String cardAuthNo,
            int cardInstCount,          // 0=일시불
            boolean usedCardPoint,
            String bankCorpCode,
            String bankAccountNo,       // 마스킹
            String productName,
            boolean settleExpected,     // 정산금/수수료 계산 완료 여부
            long settleExpectAmount,    // settleExpected=false면 의미 없음(0)
            long payCommissionAmount,   // settleExpected=false면 의미 없음(0)
            String merchantExtraParameter,
            String merchantPayTransactionKey,
            String userIdentifier,      // 암호화된 식별값
            boolean extraDeduction,
            String useCfmYmdt,          // yyyymmdd
            NaverPayApproveResponse.SubMerchantInfo subMerchantInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubMerchantInfo(
            String subMerchantName,
            String subMerchantId,
            String subMerchantBusinessNo,
            String subMerchantPayId,
            String subMerchantTelephoneNo,
            String subMerchantCustomerServiceUrl
    ) {}
}
