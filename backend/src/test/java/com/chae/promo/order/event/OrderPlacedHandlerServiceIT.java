package com.chae.promo.order.event;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.entity.UserType;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.repository.ProductRepository;
import com.chae.promo.support.RetryTestAspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


@SpringBootTest
@ActiveProfiles("dev")
@Import({RetryTestAspect.class})
class OrderPlacedHandlerServiceIT {

    @Autowired
    ProductRepository repo;
    @Autowired
    OrderPlacedHandlerService service;

    @Autowired
    private RetryTestAspect retryAspect;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("동시성 검증 - 재시도 성공")
    void twoConcurrentTx_bothShouldSucceedWithRetry() throws Exception {
        retryAspect.reset();

        var p = repo.findAll().iterator().next();
        p.setStockQuantity(10);
        repo.save(p);


        // 미리 Order 생성
        String orderPublicId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .publicId(orderPublicId)
                .status(OrderStatus.CREATED)
                .ordererName("비회원 - test")
                .totalPrice(BigDecimal.valueOf(1000L))
                .build();
        orderRepository.save(order);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(2);

        Runnable r = () -> {
            try {
                assertDoesNotThrow(() -> {
                    OrderPlacedEvent event = OrderPlacedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(UUID.randomUUID().toString())
                            .userType(UserType.GUEST)
                            .orderPublicId(orderPublicId)
                            .items(List.of(
                                    OrderPlacedEvent.Item.builder()
                                            .productCode(p.getCode())
                                            .decreasedStock(1)
                                            .build()
                            ))
                            .build();
                    service.processProductStockChange(event);
                });
            } finally {
                done.countDown();
            }
        };

        pool.submit(r);
        pool.submit(r);
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 둘 다 성공 → 재고는 8
        Product reloaded = repo.findById(p.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(8L);


        //order 상태 변경 검증
        Order reloadedOrder = orderRepository.findByPublicId(orderPublicId)
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        retryAspect.printStatistics();
        assertThat(retryAspect.getCallCountForMethod("processProductStockChange"))
                .isGreaterThan(2);

    }
}