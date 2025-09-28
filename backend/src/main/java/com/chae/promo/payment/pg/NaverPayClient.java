package com.chae.promo.payment.pg;

import com.chae.promo.payment.dto.NaverPayApproveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;
import java.util.function.Consumer;

//외부 API 호출용 클라이언트
@Service
@RequiredArgsConstructor
public class NaverPayClient {
    private final WebClient client;
    @Value("${naverpay.partner-id}") String partnerId;
    @Value("${naverpay.client-id}") String clientId;
    @Value("${naverpay.client-secret}") String clientSecret;

    private Consumer<HttpHeaders> auth(String idem) {
        return h -> {
            h.add("X-Naver-Client-Id", clientId);
            h.add("X-Naver-Client-Secret", clientSecret);
            h.add("X-NaverPay-Idempotency-Key", idem); // 멱등키 권장
        };
    }

    // 결제승인: paymentId로 승인
    public NaverPayApproveResponse approve(String paymentId) {
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();

        form.add("paymentId", paymentId);

        NaverPayApproveResponse response = client.post()
                .uri("/" + partnerId + "/naverpay/payments/v2/apply/payment")
                .headers(auth(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(NaverPayApproveResponse.class)
                .block();

        return response;
    }
}