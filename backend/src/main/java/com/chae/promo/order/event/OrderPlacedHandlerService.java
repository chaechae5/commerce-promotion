package com.chae.promo.order.event;

import com.chae.promo.exception.CommonCustomException;
import com.chae.promo.exception.CommonErrorCode;
import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.product.dto.ProductRequest;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.entity.ProductStockAudit;
import com.chae.promo.product.repository.ProductBulkRepository;
import com.chae.promo.product.repository.ProductStockAuditRepository;
import com.chae.promo.product.util.ProductValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@RequiredArgsConstructor
@Service
public class OrderPlacedHandlerService {
    private final ProductStockAuditRepository productStockAuditRepository;
    private final ProductBulkRepository productBulkRepository;
    private final ProductValidator productValidator;
    private final StockRedisService stockRedisService;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_DELAY = 100L;
    private static final long MAX_BACKOFF_DELAY = 1000L;


    public void processProductStockChange(OrderPlacedEvent event) {

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {



            try {

                stockProcess(event);

                return; // 성공 시 메서드 종료

            } catch (CommonCustomException e) {
                log.warn("비즈니스 로직 검증 실패 - eventId: {}, error: {}", event.getEventId(), e.getMessage());
                throw e; //재시도 제외 예외

            } catch (DataIntegrityViolationException e) {
                // 재시도할 필요 없는 예외는 DLT로 바로 전송
                log.warn("Audit 저장 실패 (데이터 무결성 위반). eventId: {}, userId: {}, e: {}",
                        event.getEventId(), event.getUserId(), e.getMessage());
                throw new CommonCustomException(CommonErrorCode.PRODUCT_STOCK_AUDIT_SAVE_FAILED);

            }  catch (OptimisticLockingFailureException e) {
                log.warn("재고 업데이트 실패 - OptimisticLockException 발생. eventId: {}, userId: {}, attempt: {}/{}",
                        event.getEventId(), event.getUserId(), attempt + 1, MAX_RETRIES);

                if (attempt >= MAX_RETRIES -1) {
                    handleFinalFailure(e, event);
                }

                applyBackoff(attempt);


            } catch (Exception e) {
                log.error("DB 저장 실패로 인한 재처리 요청. eventId: {}, attempt: {}/{}", event.getEventId(), attempt + 1, MAX_RETRIES, e);

                if (attempt >= MAX_RETRIES -1) {
                    handleFinalFailure(e, event);
                }

               applyBackoff(attempt);
            }
        }
    }

    //각 시도 별로 새로운 트랜잭션에서 실행
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stockProcess(OrderPlacedEvent event) {
        List<OrderPlacedEvent.Item> itemList = event.getItems();

        log.debug("재고 처리 트랜잭션 시작 - eventId: {}, 상품 수: {}",
                event.getEventId(), itemList.size());

        //상품 정보 조회 및 검증
        Map<String, Product> productMap = getAndValidateProduct(itemList);

        //상품 재고 변경 및 audit 기록
        applyStockChangeAndSaveAudit(event, itemList, productMap);

        //redis 재고 확정
        confirmStockInRedis(itemList, event.getOrderPublicId());

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


    private void confirmStockInRedis(List<OrderPlacedEvent.Item> itemList, String orderPublicId) {
        itemList.forEach(item ->
                stockRedisService.confirm(item.getProductCode(), orderPublicId)
        );
    }

    private void handleFinalFailure(Exception e, OrderPlacedEvent event) {
        log.error("최대 재시도 초과 - eventId: {}", event.getEventId(), e);
        throw (e instanceof RuntimeException re) ? re : new RuntimeException(e); // 재시도 실패 시 예외를 던져 DLT로 전송되도록 함
    }

    private void applyBackoff(int attemptNumber) {
        try {

            // Exponential backoff: 100ms, 200ms, 400ms...
            long delay = Math.min(
                    (long) Math.pow(2, attemptNumber) * INITIAL_BACKOFF_DELAY,
                    MAX_BACKOFF_DELAY
            );

            // 지터(jitter) 추가 - ±20% 랜덤 변동으로 thundering herd 방지
            double jitterFactor = 0.8 + (ThreadLocalRandom.current().nextDouble() * 0.4);
            delay = (long) (delay * jitterFactor);
            delay = Math.max(delay, INITIAL_BACKOFF_DELAY); // 최소값 보장

            log.info("백오프 적용 중... delay: {}ms", delay);
            Thread.sleep(delay);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("백오프 중 인터럽트 발생", e);
            throw new RuntimeException("백오프 중 인터럽트 발생", e);
        }
    }


}
