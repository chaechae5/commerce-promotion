package com.chae.promo.order.service;

import com.chae.promo.common.kafka.TopicNames;
import com.chae.promo.common.util.UuidUtil;
import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.order.dto.OrderRequest;
import com.chae.promo.order.dto.OrderResponse;
import com.chae.promo.order.dto.PurchaseItemDTO;
import com.chae.promo.order.entity.*;
import com.chae.promo.order.mapper.OrderMapper;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.order.repository.ShippingInfoRepository;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.outbox.service.OutboxService;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.util.ProductValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final StockRedisService stockRedisService;
    private final ProductValidator productValidator;
    private final OrderMapper orderMapper;
    private static final long redisHoldTtlSec = 60 * 10; // 10분 TTL
    private final ShippingInfoRepository shippingInfoRepository;


    @Transactional
    @Override
    public OrderResponse.Purchase placeOrder(OrderRequest.Purchase request, String userId) {

        //현재 비회원명 임시 사용
        String ordererName = "비회원 " + userId;

        //상품 유효성 검증
        Map<String, Product> productMap = validateAndGetProductMap(request);

        //주문 생성 및 저장
        Order order = createAndSaveOrder(request, ordererName, productMap);
        orderRepository.flush();

        // 재고 예약
        String orderId = order.getPublicId();
        reserveStockInRedis(request.getItems(), orderId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if( status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    // 주문이 롤백되면 Redis 예약 취소
                    cancelStockInRedisQuietly(request.getItems(), orderId);
                }
            }
        });

        return orderMapper.toPurchaseResponse(order);
    }

    private Map<String, Product> validateAndGetProductMap(OrderRequest.Purchase request){
        // 요청된 상품 코드로 Product 엔티티를 한번에 조회하고, Map으로 변환
        try {
            return productValidator.getAndValidateProductMap(
                    request.getItems(),
                    PurchaseItemDTO::getProductCode,
                    PurchaseItemDTO::getQuantity
            );

        } catch (CommonCustomException e) {
            log.warn("상품 유효성 검증 실패: {}", e.getMessage());
            throw e;
        }
    }

    private Order createAndSaveOrder(OrderRequest.Purchase request, String ordererName, Map<String, Product> productMap){
        Order order = Order.builder()
                .customerId(null)
                .ordererName(ordererName)
                .publicId(UuidUtil.generate())
                .status(OrderStatus.CREATED) // 초기 상태는 결제 대기
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (PurchaseItemDTO item : request.getItems()) {
            Product product = productMap.get(item.getProductCode());

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);

            //총 금액 계산 (단가 * 수량)
            totalPrice = totalPrice.add(
                    product.getPrice().multiply(new BigDecimal(item.getQuantity()))
            );
        }

        order.updateTotalPrice(totalPrice);

        return orderRepository.save(order);
    }

    private void reserveStockInRedis(List<PurchaseItemDTO> items, String orderId){
        for (PurchaseItemDTO item : items) {
            long requestedQuantity = item.getQuantity();

            try {
                // Redis Lua 스크립트를 통해 재고를 원자적으로 차감 시도
                stockRedisService.reserve(item.getProductCode(), orderId , requestedQuantity, redisHoldTtlSec);

            } catch (CommonCustomException e) {
                log.warn("Redis 재고 예약 실패 (비즈니스 예외). productCode:{}, orderId: {}, quantity:{}, e:{}",
                        item.getProductCode(), orderId, requestedQuantity, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Redis 재고 차감 중 예상치 못한 오류 발생. orderId:{}, e: {}",
                        orderId, e);
                throw new RuntimeException("Redis 재고 예약 중 알 수 없는 오류가 발생했습니다.");

            }
        }
    }

    private void cancelStockInRedisQuietly(List<PurchaseItemDTO> items, String orderId) {
        for (var item : items) {
            try {
                stockRedisService.cancel(item.getProductCode(), orderId);
            } catch (Exception ex) {
                // 멱등/보상 성격이므로 조용히 로그만
                log.warn("Redis 예약 취소 중 오류(무시) productCode: {}, orderId: {}, err: {}",
                        item.getProductCode(), orderId, ex.toString());
            }
        }
    }


    @Transactional
    @Override
    public OrderResponse.OrderSummary createOrder(OrderRequest.Create request, String userId) {
        //현재 비회원명 임시 사용
        String ordererName = "비회원 " + userId;

        //상품 유효성 검증
        Map<String, Product> productMap = validateAndGetProductMap(request);

        //주문 생성 및 저장
        Order order = createAndSaveOrder(request, ordererName, productMap);

        //배송 정보 저장
        createAndSaveShippingInfo(order, request.getShippingInfo());

        // 재고 예약
        String orderId = order.getPublicId();
        reserveStockInRedis(request.getItems(), orderId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if( status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    // 주문이 롤백되면 Redis 예약 취소
                    cancelStockInRedisQuietly(request.getItems(), orderId);
                }
            }
        });

        return OrderResponse.OrderSummary.builder()
                .publicId(order.getPublicId())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus().name())
                .itemCount(order.getOrderItems().size())
                .build();
    }

    private Map<String, Product> validateAndGetProductMap(OrderRequest.Create request){
        // 요청된 상품 코드로 Product 엔티티를 한번에 조회하고, Map으로 변환
        try {
            return productValidator.getAndValidateProductMap(
                    request.getItems(),
                    PurchaseItemDTO::getProductCode,
                    PurchaseItemDTO::getQuantity
            );

        } catch (CommonCustomException e) {
            log.warn("상품 유효성 검증 실패: {}", e.getMessage());
            throw e;
        }
    }

    private Order createAndSaveOrder(OrderRequest.Create request, String ordererName, Map<String, Product> productMap){
        String productName = extractProductName(request.getItems(), productMap);

        Order order = Order.builder()
                .customerId(null)
                .ordererName(ordererName)
                .publicId(UuidUtil.generate())
                .status(OrderStatus.CREATED) // 초기 상태는 주문 생성
                .productName(productName)
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (PurchaseItemDTO item : request.getItems()) {
            Product product = productMap.get(item.getProductCode());

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);

            //총 금액 계산 (단가 * 수량)
            totalPrice = totalPrice.add(
                    product.getPrice().multiply(new BigDecimal(item.getQuantity()))
            );
        }

        order.updateTotalPrice(totalPrice);

        return orderRepository.save(order);
    }

    private ShippingInfo createAndSaveShippingInfo(Order order, OrderRequest.ShippingInfo shippingInfoRequest) {
        ShippingInfo shippingInfo = ShippingInfo.builder()
                .order(order)
                .recipientName(shippingInfoRequest.getRecipientName())
                .zipcode(shippingInfoRequest.getZipcode())
                .address(shippingInfoRequest.getAddress())
                .phoneNumber(shippingInfoRequest.getPhoneNumber())
                .memo(shippingInfoRequest.getMemo())
                .build();

        return shippingInfoRepository.save(shippingInfo);
    }

    // 주문 상품명 추출 및 가공
    private String extractProductName(List<PurchaseItemDTO> items, Map<String, Product> productMap) {
        int PRODUCT_NAME_LIMIT = 100;

        if (items == null || items.isEmpty()) return "상품 없음";

        // 첫 상품명
        Product first = productMap.get(items.get(0).getProductCode());
        String firstName = (first != null && first.getName() != null) ? first.getName().trim() : "상품";

        // “외 N건” = (품목 수 - 1)
        int extra = Math.max(0, items.size() - 1);
        String suffix = (extra > 0) ? " 외 " + extra + "건" : "";

        // 100자 제한
        int baseLimit = Math.max(0, PRODUCT_NAME_LIMIT - suffix.codePointCount(0, suffix.length()));
        String head = limitByCodePoints(firstName, baseLimit);
        String name = head + suffix;

        return name;
    }

    private static String limitByCodePoints(String s, int max) {
        if (s == null || max <= 0) return "";
        int end = s.offsetByCodePoints(0, Math.min(max, s.codePointCount(0, s.length())));
        return s.substring(0, end); // count<=max면 end == s.length()

    }

}
