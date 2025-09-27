package com.chae.promo.order.event;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPlacedHandlerServiceTest {

    private OrderPlacedEvent event;

    @BeforeEach
    void setup() {
        event = OrderPlacedEvent.builder()
                .eventId("E1")
                .orderPublicId("O1")
                .userId("U1")
                .items(List.of(OrderPlacedEvent.Item.builder()
                        .productCode("P1")
                        .decreasedStock(2)
                        .build()))
                .build();

    }

    @Test
    @DisplayName("정상 처리")
    void successImmediately() {
        SuccessStubProcessor processor = new SuccessStubProcessor();
        OrderPlacedHandlerService handler = new OrderPlacedHandlerService(processor);

        handler.processProductStockChange(event);

        assertEquals(1, processor.callCount);
    }

    @Test
    @DisplayName("비즈니스 예외 - 재시도 없이 바로 실패")
    void businessException_noRetry() {
        FailStubProcessor processor = new FailStubProcessor(
                new CommonCustomException(CommonErrorCode.COUPON_ALREADY_ISSUED));
        OrderPlacedHandlerService handler = new OrderPlacedHandlerService(processor);

        assertThrows(CommonCustomException.class,
                () -> handler.processProductStockChange(event));

        assertEquals(1, processor.callCount); // 재시도 안 됨
    }

    @Test
    @DisplayName("DataIntegrityViolationException - 재시도 없이 바로 실패, 공통 예외로 변환")
    void dataIntegrityViolation_transformed() {
        FailStubProcessor processor = new FailStubProcessor(new DataIntegrityViolationException("DB fail"));
        OrderPlacedHandlerService handler = new OrderPlacedHandlerService(processor);

        CommonCustomException ex = assertThrows(CommonCustomException.class,
                () -> handler.processProductStockChange(event));

        assertEquals(CommonErrorCode.PRODUCT_STOCK_AUDIT_SAVE_FAILED, ex.getErrorCode());
        assertEquals(1, processor.callCount);
    }

    @Test
    @DisplayName("낙관적 락 예외 - 최대 3회 재시도 후 실패")
    void optimisticLocking_retry3Times() {
        FailStubProcessor processor = new FailStubProcessor(new OptimisticLockingFailureException("fail"));
        OrderPlacedHandlerService handler = new OrderPlacedHandlerService(processor);

        assertThrows(OptimisticLockingFailureException.class,
                () -> handler.processProductStockChange(event));

        assertEquals(3, processor.callCount); // 최대 3회 재시도
    }

    @Test
    @DisplayName("런타임 예외 - 최대 3회 재시도 후 실패")
    void runtimeException_retry3Times() {
        FailStubProcessor processor = new FailStubProcessor(new RuntimeException("system fail"));
        OrderPlacedHandlerService handler = new OrderPlacedHandlerService(processor);

        assertThrows(RuntimeException.class,
                () -> handler.processProductStockChange(event));

        assertEquals(3, processor.callCount); // 최대 3회 재시도
    }



    static class SuccessStubProcessor implements OrderStockProcessor {
        int callCount = 0;

        @Override
        public void processStockDecrease(OrderPlacedEvent event) {
            callCount++; // 그냥 정상 동작했다고 가정
        }
    }

    static class FailStubProcessor implements OrderStockProcessor {
        int callCount = 0;
        private final RuntimeException toThrow;

        FailStubProcessor(RuntimeException toThrow) {
            this.toThrow = toThrow;
        }

        @Override
        public void processStockDecrease(OrderPlacedEvent event) {
            callCount++;
            throw toThrow;
        }
    }
}
