package com.chae.promo.order.event;

import com.chae.promo.order.service.redis.StockRedisService;
import com.chae.promo.product.dto.ProductRequest;
import com.chae.promo.product.entity.Product;
import com.chae.promo.product.entity.ProductStockAudit;
import com.chae.promo.product.repository.ProductBulkRepository;
import com.chae.promo.product.repository.ProductStockAuditRepository;
import com.chae.promo.product.util.ProductValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


@Slf4j
@RequiredArgsConstructor
@Service
public class OrderStockProcessorImpl implements OrderStockProcessor{

    private final ProductStockAuditRepository productStockAuditRepository;
    private final ProductBulkRepository productBulkRepository;
    private final ProductValidator productValidator;
    private final StockRedisService stockRedisService;

    /**
     * 주문으로 인한 상품 재고 감소 처리
     * - 신규 트랜잭션으로 처리
     * - 상품 검증, DB 재고 차감, 재고 변경 이력 저장, Redis 재고 확정
     *
     * @param event 주문 완료 이벤트
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processStockDecrease(OrderPlacedEvent event) {
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

}
