package com.chae.promo.order.event;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.entity.Order;
import com.chae.promo.order.entity.OrderStatus;
import com.chae.promo.order.repository.OrderRepository;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.product.dto.ProductRequest;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.entity.ProductStockAudit;
import com.chae.promo.product.repository.ProductBulkRepository;
import com.chae.promo.product.repository.ProductStockAuditRepository;
import com.chae.promo.product.util.ProductValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Slf4j
@RequiredArgsConstructor
@Service
public class OrderPlacedHandlerService {
    private final ProductStockAuditRepository productStockAuditRepository;
    private final ProductBulkRepository productBulkRepository;
    private final ProductValidator productValidator;
    private final OrderRepository orderRepository;
    private final StockRedisService stockRedisService;


    @Retryable(
            value = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000, random = true)
    )
    @Transactional
    public void processProductStockChange(OrderPlacedEvent event) {

        List<OrderPlacedEvent.Item> itemList = event.getItems();

        try {

            //상품 정보 조회 및 검증
            Map<String, Product> productMap = getAndValidateProduct(itemList);

            //상품 재고 변경 및 audit 기록
            applyStockChangeAndSaveAudit(event, itemList, productMap);


//            //order 상태 변경 (PENDING_PAYMENT)
//            markOrderPendingPayment(event.getOrderPublicId());

            //redis 재고 확정
            confirmStockInRedis(itemList, event.getOrderPublicId());


        } catch (CommonCustomException e) {
            log.warn("비즈니스 로직 검증 실패 - eventId: {}, error: {}", event.getEventId(), e.getMessage());
            throw e; //재시도 제외 예외
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("재고 업데이트 실패 - OptimisticLockException 발생. eventId: {}, userId: {}, itemList: {}",
                    event.getEventId(), event.getUserId(), itemList);
            throw e; //재시도 제외 예외 : 서비스 레벨에서 처리
        } catch (DataIntegrityViolationException e) {
            // 재시도할 필요 없는 예외는 DLT로 바로 전송
            log.warn("Audit 저장 실패 (데이터 무결성 위반). eventId: {}, userId: {}, e: {}",
                    event.getEventId(), event.getUserId(), e.getMessage());
            throw new CommonCustomException(CommonErrorCode.PRODUCT_STOCK_AUDIT_SAVE_FAILED);
        } catch (Exception e) {
            log.error("DB 저장 실패로 인한 재처리 요청. eventId: {}", event.getEventId(), e);

            // Spring Kafka의 재시도(Retry) 및 DLQ(Dead Letter Queue) 메커니즘을 활성화하는 역할
            throw e;
        }
    }

    private Map<String, Product> getAndValidateProduct(List<OrderPlacedEvent.Item> itemList) {
        return productValidator.getAndValidateProductMap(
                itemList,
                OrderPlacedEvent.Item::getProductCode,
                OrderPlacedEvent.Item::getDecreasedStock);
    }

    private ProductStockAudit prepareAudit(OrderPlacedEvent.Item item,
                                           Product product,
                                           OrderPlacedEvent event) {


        long changedQuantity = item.getDecreasedStock();
        long newStock = product.getStockQuantity() - changedQuantity;

        return ProductStockAudit.builder()
                .productId(product.getId())
                .changeType(ProductStockAudit.ChangeType.DECREASE)
                .changeQuantity(item.getDecreasedStock())
                .currentStock(newStock)
                .changedByUserId(event.getUserId())
                .userType(event.getUserType())
                .orderPublicId(event.getOrderPublicId())
                .eventId(event.getEventId())
                .description("주문으로 인한 재고 감소")
                .build();

    }

    private ProductRequest.StockBulkUpdate prepareStockUpdate(OrderPlacedEvent.Item item, Product product) {
        return ProductRequest.StockBulkUpdate.builder()
                .productId(product.getId())
                .decreasedStock(item.getDecreasedStock())
                .version(product.getVersion())
                .build();
    }

    private void applyStockChangeAndSaveAudit(OrderPlacedEvent event, List<OrderPlacedEvent.Item> itemList, Map<String, Product> productMap) {
        // audit 객체 및 재고 업데이트 DTO 생성
        List<ProductStockAudit> audits = itemList.stream()
                .map(item -> prepareAudit(item, productMap.get(item.getProductCode()), event))
                .toList();

        List<ProductRequest.StockBulkUpdate> bulkUpdates = itemList.stream()
                .map(item -> prepareStockUpdate(item, productMap.get(item.getProductCode())))
                .toList();

        // DB 재고 차감 및 audit 저장
        // DB 재고 차감 (JDBC)
        productBulkRepository.bulkUpdateStock(bulkUpdates);

        //Audit 저장 (JPA) + 조기 검증을 위한 flush
        productStockAuditRepository.saveAll(audits);
        productStockAuditRepository.flush();

        log.info("DB 재고 차감 및 audit 기록 성공. eventId: {}, orderPublicId: {}, userId: {}, 처리된 상품 수: {}",
                event.getEventId(), event.getOrderPublicId(), event.getUserId(), itemList.size());

    }

    @Recover
    public void recover(OptimisticLockingFailureException e, OrderPlacedEvent event) {
        log.error("최대 재시도 초과 - eventId: {}", event.getEventId(), e);
        throw e; // 재시도 실패 시 예외를 던져 DLT로 전송되도록 함
    }

    private void markOrderPendingPayment(String orderPublicId){
        Order order = orderRepository.findByPublicId(orderPublicId)
                .orElseThrow(() -> new CommonCustomException(CommonErrorCode.ORDER_NOT_FOUND));

        order.setStatus(OrderStatus.PENDING_PAYMENT);
    }

    private void confirmStockInRedis(List<OrderPlacedEvent.Item> itemList, String orderPublicId){
        itemList.forEach(item ->
                stockRedisService.confirm(item.getProductCode(), orderPublicId)
        );
    }
}
