package com.chae.promo.order.service;

/**
 * Tested: OrderServiceImpl.createOrder()
 *
 * Cases:
 *  1. 배송지 포함한 주문 생성 성공 — productMap 반환, save/shippingInfo save, reserve 호출, OrderSummary 반환 검증
 *  2. 재고 부족 시 예외 — productValidator가 INSUFFICIENT_STOCK 예외를 던지면 그대로 전파
 *  3. 상품 없음 예외 — productValidator가 PRODUCT_NOT_FOUND 예외를 던지면 그대로 전파
 */

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.dto.PurchaseItemDTO;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.entity.ShippingInfo;
import com.chae.promo.order.mapper.OrderMapper;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.order.repository.ShippingInfoRepository;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.entity.ProductStatus;
import com.chae.promo.product.util.ProductValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl.createOrder() 단위 테스트")
class CreateOrderServiceTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockRedisService stockRedisService;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ShippingInfoRepository shippingInfoRepository;

    private MockedStatic<TransactionSynchronizationManager> txSyncMock;

    @BeforeEach
    void setUp() {
        txSyncMock = mockStatic(TransactionSynchronizationManager.class);
        txSyncMock.when(() -> TransactionSynchronizationManager.registerSynchronization(
                any(TransactionSynchronization.class))).thenAnswer(inv -> null);
    }

    @AfterEach
    void tearDown() {
        txSyncMock.close();
    }

    // ──────────────────────────────── 헬퍼 ────────────────────────────────

    private Product product(String code, String name, long price, long stock) {
        return Product.builder()
                .code(code)
                .name(name)
                .price(BigDecimal.valueOf(price))
                .stockQuantity(stock)
                .status(ProductStatus.FOR_SALE)
                .build();
    }

    private PurchaseItemDTO item(String code, long qty) {
        return PurchaseItemDTO.builder()
                .productCode(code)
                .quantity(qty)
                .build();
    }

    private OrderRequest.ShippingInfo shippingInfo() {
        return OrderRequest.ShippingInfo.builder()
                .recipientName("김초코")
                .address("서울특별시 용산구 한강대로 1")
                .zipcode("04379")
                .phoneNumber("010-1234-5678")
                .memo("문 앞에 놓아주세요")
                .build();
    }

    // ──────────────────────────────── 테스트 ─────────────────────────────

    @Test
    @DisplayName("배송지 포함한 주문 생성 성공 — OrderSummary publicId, totalPrice, status, itemCount 검증")
    void createOrder_success_withShippingInfo() {
        // Arrange
        String userId = "guest-create-1";
        PurchaseItemDTO i1 = item("P-001", 2L);
        PurchaseItemDTO i2 = item("P-002", 3L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1, i2))
                .shippingInfo(shippingInfo())
                .build();

        Product p1 = product("P-001", "상품A", 1000L, 100L);
        Product p2 = product("P-002", "상품B", 2000L, 50L);
        Map<String, Product> productMap = Map.of("P-001", p1, "P-002", p2);

        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()), any(), any())
        ).willReturn(productMap);

        // orderRepository.save returns the passed Order instance (populated by createAndSaveOrder)
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // shippingInfoRepository.save returns any ShippingInfo
        given(shippingInfoRepository.save(any(ShippingInfo.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        OrderResponse.OrderSummary result = orderService.createOrder(request, userId);

        // Assert — returned summary is built directly from the Order entity (not a mapper)
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED.name());
        // totalPrice = 1000*2 + 2000*3 = 8000
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(8000L));
        assertThat(result.getItemCount()).isEqualTo(2);
        assertThat(result.getPublicId()).isNotBlank();

        // Shipping info must be persisted exactly once
        then(shippingInfoRepository).should(times(1)).save(any(ShippingInfo.class));

        // Redis reserve must be called once per item
        then(stockRedisService).should(times(1))
                .reserve(eq("P-001"), anyString(), eq(2L), anyLong());
        then(stockRedisService).should(times(1))
                .reserve(eq("P-002"), anyString(), eq(3L), anyLong());
    }

    @Test
    @DisplayName("배송지 포함한 단일 상품 주문 성공 — 외 N건 없이 productName 단독 설정")
    void createOrder_success_singleItem_productNameWithoutSuffix() {
        // Arrange
        String userId = "guest-create-2";
        PurchaseItemDTO i1 = item("P-010", 1L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(shippingInfo())
                .build();

        Product p1 = product("P-010", "단독상품", 5000L, 10L);

        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()), any(), any())
        ).willReturn(Map.of("P-010", p1));

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(shippingInfoRepository.save(any(ShippingInfo.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        OrderResponse.OrderSummary result = orderService.createOrder(request, userId);

        // Assert
        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(5000L));
        assertThat(result.getItemCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED.name());

        then(stockRedisService).should(times(1))
                .reserve(eq("P-010"), anyString(), eq(1L), anyLong());
    }

    @Test
    @DisplayName("재고 부족 시 예외 — productValidator가 INSUFFICIENT_STOCK 던지면 createOrder에서 전파")
    void createOrder_insufficientStock_throwsCommonCustomException() {
        // Arrange
        String userId = "guest-create-fail";
        PurchaseItemDTO i1 = item("P-OUT", 999L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(shippingInfo())
                .build();

        willThrow(new CommonCustomException(CommonErrorCode.INSUFFICIENT_STOCK))
                .given(productValidator)
                .getAndValidateProductMap(eq(request.getItems()), any(), any());

        // Act & Assert
        assertThatExceptionOfType(CommonCustomException.class)
                .isThrownBy(() -> orderService.createOrder(request, userId))
                .satisfies(ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(CommonErrorCode.INSUFFICIENT_STOCK));

        // Order and shipping info must NOT be saved
        then(orderRepository).should(never()).save(any());
        then(shippingInfoRepository).should(never()).save(any());
        // Redis must NOT be touched
        then(stockRedisService).should(never()).reserve(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("상품 없음 예외 — productValidator가 PRODUCT_NOT_FOUND 던지면 createOrder에서 전파")
    void createOrder_productNotFound_throwsCommonCustomException() {
        // Arrange
        String userId = "guest-no-product";
        PurchaseItemDTO i1 = item("P-GHOST", 1L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(shippingInfo())
                .build();

        willThrow(new CommonCustomException(CommonErrorCode.PRODUCT_NOT_FOUND))
                .given(productValidator)
                .getAndValidateProductMap(eq(request.getItems()), any(), any());

        // Act & Assert
        assertThatExceptionOfType(CommonCustomException.class)
                .isThrownBy(() -> orderService.createOrder(request, userId))
                .satisfies(ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(CommonErrorCode.PRODUCT_NOT_FOUND));

        then(orderRepository).should(never()).save(any());
        then(shippingInfoRepository).should(never()).save(any());
        then(stockRedisService).should(never()).reserve(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Redis 재고 예약 실패 시 CommonCustomException 전파 — 배송 정보는 이미 저장된 상태")
    void createOrder_redisReserveFails_throwsCommonCustomException() {
        // Arrange
        String userId = "guest-redis-fail";
        PurchaseItemDTO i1 = item("P-SOLD", 5L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(shippingInfo())
                .build();

        Product p1 = product("P-SOLD", "매진상품", 3000L, 100L);
        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()), any(), any())
        ).willReturn(Map.of("P-SOLD", p1));

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(shippingInfoRepository.save(any(ShippingInfo.class))).willAnswer(inv -> inv.getArgument(0));

        willThrow(new CommonCustomException(CommonErrorCode.PRODUCT_SOLD_OUT))
                .given(stockRedisService)
                .reserve(eq("P-SOLD"), anyString(), eq(5L), anyLong());

        // Act & Assert
        assertThatExceptionOfType(CommonCustomException.class)
                .isThrownBy(() -> orderService.createOrder(request, userId))
                .satisfies(ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(CommonErrorCode.PRODUCT_SOLD_OUT));

        // Order and shipping were saved before reserve was attempted
        then(orderRepository).should(times(1)).save(any(Order.class));
        then(shippingInfoRepository).should(times(1)).save(any(ShippingInfo.class));
    }

    @Test
    @DisplayName("Redis 예약 중 예상치 못한 예외 발생 시 RuntimeException으로 감싸서 전파")
    void createOrder_redisUnexpectedException_wrapsAsRuntimeException() {
        // Arrange
        String userId = "guest-redis-unknown";
        PurchaseItemDTO i1 = item("P-ERR", 1L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(shippingInfo())
                .build();

        Product p1 = product("P-ERR", "에러상품", 1000L, 10L);
        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()), any(), any())
        ).willReturn(Map.of("P-ERR", p1));

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(shippingInfoRepository.save(any(ShippingInfo.class))).willAnswer(inv -> inv.getArgument(0));

        // A low-level unexpected exception (e.g., Redis connection failure)
        willThrow(new RuntimeException("connection reset"))
                .given(stockRedisService)
                .reserve(eq("P-ERR"), anyString(), eq(1L), anyLong());

        // Act & Assert — OrderServiceImpl.reserveStockInRedis re-wraps non-CommonCustomException
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> orderService.createOrder(request, userId))
                .withMessageContaining("Redis 재고 예약 중 알 수 없는 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("ordererName은 '비회원 ' + userId 형식으로 설정된다")
    void createOrder_ordererName_isFormattedCorrectly() {
        // Arrange
        String userId = "u-42";
        PurchaseItemDTO i1 = item("P-NAME", 1L);

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(shippingInfo())
                .build();

        Product p1 = product("P-NAME", "이름테스트상품", 2000L, 20L);
        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()), any(), any())
        ).willReturn(Map.of("P-NAME", p1));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        given(orderRepository.save(orderCaptor.capture())).willAnswer(inv -> inv.getArgument(0));
        given(shippingInfoRepository.save(any(ShippingInfo.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        orderService.createOrder(request, userId);

        // Assert
        assertThat(orderCaptor.getValue().getOrdererName()).isEqualTo("비회원 u-42");
    }

    @Test
    @DisplayName("ShippingInfo는 저장된 Order 인스턴스와 연결되어 저장된다")
    void createOrder_shippingInfo_isSavedWithCorrectOrderAndFields() {
        // Arrange
        String userId = "guest-shipping";
        PurchaseItemDTO i1 = item("P-SHIP", 1L);
        OrderRequest.ShippingInfo si = shippingInfo();

        OrderRequest.Create request = OrderRequest.Create.builder()
                .items(List.of(i1))
                .shippingInfo(si)
                .build();

        Product p1 = product("P-SHIP", "배송상품", 1500L, 5L);
        given(productValidator.getAndValidateProductMap(
                eq(request.getItems()), any(), any())
        ).willReturn(Map.of("P-SHIP", p1));

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<ShippingInfo> shippingCaptor = ArgumentCaptor.forClass(ShippingInfo.class);
        given(shippingInfoRepository.save(shippingCaptor.capture())).willAnswer(inv -> inv.getArgument(0));

        // Act
        orderService.createOrder(request, userId);

        // Assert
        ShippingInfo captured = shippingCaptor.getValue();
        assertThat(captured.getRecipientName()).isEqualTo("김초코");
        assertThat(captured.getZipcode()).isEqualTo("04379");
        assertThat(captured.getAddress()).isEqualTo("서울특별시 용산구 한강대로 1");
        assertThat(captured.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(captured.getMemo()).isEqualTo("문 앞에 놓아주세요");
        assertThat(captured.getOrder()).isNotNull();
    }
}
