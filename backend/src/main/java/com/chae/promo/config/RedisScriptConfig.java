package com.chae.promo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    // [재고 차감] Redis 재고 차감 스크립트
    // KEYS[1]: productStockKey (상품 재고 키)
    // ARGV[1]: requestedCount (요청된 재고 차감 수량)
    private static final String REDIS_DECREASE_STOCK_SCRIPT = """
            local productStockKey = KEYS[1]
            local requestedCount = tonumber(ARGV[1])
                        
            -- 1. 재고 차감: 현재 재고를 확인하고 0보다 클 경우에만 차감
            local currentStock = tonumber(redis.call('GET', productStockKey))
                        
            -- 2. 재고 키가 존재하지 않으면 -2 반환
            if not currentStock then
                return -2 -- 재고 키가 존재하지 않음
            end
                        
            -- 3. 현재 재고가 요청수량보다 많거나 같은지 확인
            if currentStock >= requestedCount then
                -- 4. 재고를 차감하고, 차감 후 남은 재고량을 반환 (성공)
                return redis.call('DECRBY', productStockKey, requestedCount)
            else
                -- 5. 재고가 부족한 경우 실패 처리
                return -1 -- 재고 부족
            end
            """;


    //KEYS[1]=stock:{SKU}:available
    // KEYS[2]=stock:{SKU}:reserved
    // KEYS[3]=hold:{SKU}:{orderId}
    // KEYS[4]=hold_index:{SKU}
    //ARGV[1]=qty, ARGV[2]=ttlSec, ARGV[3]=nowMillis
    public static final String STOCK_RESERVE_SCRIPT = """
            
            local available = tonumber(redis.call('GET', KEYS[1]))
            if available == nil then
              return -2
            end
                        
            -- 멱등: 이미 hold가 있으면 성공 처리
            if redis.call('EXISTS', KEYS[3]) == 1 then
              return 1
            end
                        
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            local qty = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
                        
            if (available - reserved) < qty then
              return -1
            end
            
            -- reserved 증가             
            redis.call('INCRBY', KEYS[2], qty)
            
            -- hold 값에 qty 저장
            local result = redis.call('SET', KEYS[3], qty, 'EX', ttl, 'NX')
            if not result then
              -- hold 저장 실패 시 reserved 롤백
              redis.call('DECRBY', KEYS[2], qty)
              return -5
            end
                        
            -- 인덱스 등록(만료 시각 = now + ttl*1000; 버퍼 주려면 -120000 등 가감)
            local expireAt = now + (ttl * 1000)
            redis.call('ZADD', KEYS[4], expireAt, KEYS[3])
                        
            return 1
            
            """;

    // KEYS[1]=stock:{SKU}:available
    // KEYS[2]=stock:{SKU}:reserved
    // KEYS[3]=hold:{SKU}:{orderId}
    // KEYS[4]=hold_index:{SKU}
    public static final String STOCK_CONFIRM_SCRIPT = """
            local holdQty = tonumber(redis.call('GET', KEYS[3]))
            if not holdQty then
              return -4
            end
                        
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            if reserved < holdQty then
              return -3 -- reserved 부족 (정합성 깨짐)
            end
                        
            -- reserved 차감 + available 차감
            redis.call('DECRBY', KEYS[2], holdQty)
            redis.call('DECRBY', KEYS[1], holdQty)
                        
            -- hold 삭제 + 인덱스 제거
            redis.call('DEL', KEYS[3])
            redis.call('ZREM', KEYS[4], KEYS[3])
                        
            return 1
            """;

    // KEYS[1]=stock:{SKU}:reserved
    // KEYS[2]=hold:{SKU}:{orderId}
    // KEYS[3]=hold_index:{SKU}
    public static final String STOCK_CANCEL_SCRIPT  = """
            -- hold 확인
            local holdQty = tonumber(redis.call('GET', KEYS[2]))
              if not holdQty then
                return -2  -- hold 없음
              end
            
            local reserved = tonumber(redis.call('GET', KEYS[1]) or '0')
            if reserved >= holdQty then
              redis.call('DECRBY', KEYS[1], holdQty)
            else
              return -3  -- reserved 불일치
            end
            
            -- hold 삭제 + 인덱스 제거
            redis.call('DEL', KEYS[2])
            redis.call('ZREM', KEYS[3], KEYS[2])
            return 1
            """;

    @Bean
    public DefaultRedisScript<Long> redisDecreaseStockScript() {
        return new DefaultRedisScript<>(REDIS_DECREASE_STOCK_SCRIPT, Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> reserveStockScript() {
        return new DefaultRedisScript<>(STOCK_RESERVE_SCRIPT, Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> confirmStockScript() {
        return new DefaultRedisScript<>(STOCK_CONFIRM_SCRIPT, Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> cancelStockScript() {
        return new DefaultRedisScript<>(STOCK_CANCEL_SCRIPT, Long.class);
    }


}
