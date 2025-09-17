package com.chae.promo.order.service;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.dto.PurchaseItemDTO;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.event.OrderPlacedEventPublisher;
import com.chae.promo.order.mapper.OrderMapper;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.outbox.entity.EventOutbox;
import com.chae.promo.outbox.repository.EventOutboxRepository;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.util.ProductValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("dev")
public class PlaceOrderServiceTest {

    @Autowired
    private OrderServiceImpl orderService;

    @MockitoBean
    private ProductValidator productValidator;
    @MockitoBean private OrderRepository orderRepository;
    @MockitoBean private StockRedisService stockRedisService;
    @MockitoBean private OrderPlacedEventPublisher orderPlacedEventPublisher;
    @MockitoBean private OrderMapper orderMapper;
    @MockitoBean
    private EventOutboxRepository eventOutboxRepository;

    private static final long TTL = 600L;


    private OrderRequest.Purchase mockRequest(String code1, long qty1, String code2, long qty2) {

        PurchaseItemDTO i1 = mock(PurchaseItemDTO.class);
        given(i1.getProductCode()).willReturn(code1);
        given(i1.getQuantity()).willReturn(qty1);

        PurchaseItemDTO i2 = mock(PurchaseItemDTO.class);

        given(i2.getProductCode()).willReturn(code2);
        given(i2.getQuantity()).willReturn(qty2);

        OrderRequest.Purchase req = mock(OrderRequest.Purchase.class);
        given(req.getItems()).willReturn(List.of(i1, i2));
        return req;
    }

    private Product product(String code, long price, long stockQuantity){
        return Product.builder()
                .code(code)
                .price(BigDecimal.valueOf(price))
                .stockQuantity(stockQuantity) // 임시 재고 수량
                .build();
    }

    @Test
    @DisplayName("정상 커밋 경로: reserve → afterCommit 이벤트 발행 검증")
    void placeOrder_commit_publishesEvent_and_reservesStock() {
        // given
        String userId = "guest-1";
        OrderRequest.Purchase request = mockRequest("P-100", 2L, "P-200", 3L);

        Product p1 = product("P-100", 1000L, 100L);
        Product p2 = product("P-200", 2000L, 1L);
        Map<String, Product> productMap = Map.of("P-100", p1, "P-200", p2);

        // 상품 검증
        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()),
                any(), any())
        ).willReturn(productMap);

        // 주문 저장 동작 (save/flush)
        // save 시 totalPrice/아이템 등이 세팅된 동일 객체를 반환한다고 가정
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // mapper
        given(orderMapper.toPurchaseResponse(any(Order.class)))
                .willReturn(mock(OrderResponse.Purchase.class));

        // when
        orderService.placeOrder(request, userId);

        // then
        // 1) flush 호출
        then(orderRepository).should(atLeastOnce()).flush();

        // 2) Redis 예약 호출 검증
        then(stockRedisService).should(times(1))
                .reserve(eq("P-100"), anyString(), eq(2L), eq(TTL));
        then(stockRedisService).should(times(1))
                .reserve(eq("P-200"), anyString(), eq(3L), eq(TTL));

        // 3) Outbox 저장 검증
        ArgumentCaptor<EventOutbox> outboxCaptor = ArgumentCaptor.forClass(EventOutbox.class);
        then(eventOutboxRepository).should(times(1)).save(outboxCaptor.capture());

        EventOutbox saved = outboxCaptor.getValue();
        then(eventOutboxRepository).should(times(1)).save(outboxCaptor.capture());

        assertThat(saved.getType()).isEqualTo("order.placed");
        assertThat(saved.getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(saved.getPayloadJson()).contains("P-100");
        assertThat(saved.getPayloadJson()).contains("P-200");

    }

    @Test
    @DisplayName("롤백 경로: 동기화 등록 이후 예외 발생 → afterCompletion(ROLLBACK)에서 예약 취소 호출")
    void placeOrder_rollback_cancelsReservedStock() {
        // given
        String userId = "guest-2";
        OrderRequest.Purchase request = mockRequest("P-300", 5, "P-400", 1);

        Product p3 = product("P-300", 500L, 100L);
        Product p4 = product("P-400", 1000L, 200L);
        Map<String, Product> productMap = Map.of("P-300", p3, "P-400", p4);

        given(productValidator.getAndValidateProductMap(eq(request.getItems()), any(), any()))
                .willReturn(productMap);

        // save는 정상
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // Outbox save도 정상 동작 (rollback 대상)
        given(eventOutboxRepository.save(any(EventOutbox.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // 여기서 핵심: 트랜잭션 동기화 등록(코드상 reserve → registerSynchronization) 이후에
        // 예외가 터져야 롤백 콜백이 실행됨. 따라서 mapper에서 런타임 예외를 던지게 해서 롤백 유도.
        given(orderMapper.toPurchaseResponse(any(Order.class)))
                .willThrow(new RuntimeException("mapping failed - force rollback"));

        // when & then
        assertThatThrownBy(() -> orderService.placeOrder(request, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mapping failed");

        // reserve는 호출되었어야 함
        then(stockRedisService).should(times(1))
                .reserve(eq("P-300"), anyString(), eq(5L), anyLong());
        then(stockRedisService).should(times(1))
                .reserve(eq("P-400"), anyString(), eq(1L), anyLong());

        // Outbox 저장은 시도되었음
        then(eventOutboxRepository).should(times(1)).save(any(EventOutbox.class));

        // 롤백 후 afterCompletion(status=ROLLED_BACK)에서 cancel()이 호출되어야 함
        // → placeOrder 호출이 예외로 끝난 뒤 검증해야 취소 콜백까지 처리 완료
        then(stockRedisService).should(times(1))
                .cancel(eq("P-300"), anyString());
        then(stockRedisService).should(times(1))
                .cancel(eq("P-400"), anyString());


    }

    @Test
    @DisplayName("Redis 예약 실패시: 예외 전파, 이벤트 미발행, cancel 미호출(현재 코드 기준)")
    void placeOrder_redisReserveFails_shouldThrow_noEvent_noCancel() {
        // given
        String userId = "guest-fail";
        OrderRequest.Purchase request = mockRequest("P-100", 2L, "P-200", 3L);

        // 상품 맵
        Product p1 = product("P-100", 1000L, 100L);
        Product p2 = product("P-200", 2000L, 0L);
        Map<String, Product> productMap = Map.of("P-100", p1, "P-200", p2);

        given(productValidator.getAndValidateProductMap(eq(request.getItems()), any(), any()))
                .willReturn(productMap);

        // save는 받은 객체 그대로 반환
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // 첫 번째 아이템은 성공, 두 번째에서 실패를 유도
        // reserve(productCode, orderId, quantity, ttlSecs)
        willDoNothing().given(stockRedisService)
                .reserve(eq("P-100"), anyString(), eq(2L), eq(TTL));

        // P-200 은 실패
        willThrow(new CommonCustomException(CommonErrorCode.PRODUCT_SOLD_OUT)).given(stockRedisService)
                .reserve(eq("P-200"), anyString(), eq(3L), eq(TTL));

        // when & then
        assertThatExceptionOfType(CommonCustomException.class)
                .isThrownBy(() -> orderService.placeOrder(request, userId))
                .withMessageContaining(CommonErrorCode.PRODUCT_SOLD_OUT.getCode());

        // 첫 아이템 호출됨
        then(stockRedisService).should(times(1))
                .reserve(eq("P-100"), anyString(), eq(2L), eq(TTL));

        // 두 번째에서 실패 시도
        then(stockRedisService).should(times(1))
                .reserve(eq("P-200"), anyString(), eq(3L), eq(TTL));

        //outbox 저장안됨
        then(eventOutboxRepository).should(never()).save(any(EventOutbox.class));

        // 보상 취소 없음
        then(stockRedisService).should(never())
                .cancel(anyString(), anyString());
    }


}
