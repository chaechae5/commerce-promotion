package com.chae.promo.order.service.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisScriptCatalog {

    private final DefaultRedisScript<Long> reserveStockScript;
    private final DefaultRedisScript<Long> confirmStockScript;
    private final DefaultRedisScript<Long> cancelStockScript;

    public RedisScriptCatalog(
            @Qualifier("reserveStockScript") DefaultRedisScript<Long> reserveStockScript,
            @Qualifier("confirmStockScript") DefaultRedisScript<Long> confirmStockScript,
            @Qualifier("cancelStockScript") DefaultRedisScript<Long> cancelStockScript
    ) {
        this.reserveStockScript = reserveStockScript;
        this.confirmStockScript = confirmStockScript;
        this.cancelStockScript = cancelStockScript;
    }

    public DefaultRedisScript<Long> reserveStock() {
        return reserveStockScript;
    }

    public DefaultRedisScript<Long> confirmStock() {
        return confirmStockScript;
    }

    public DefaultRedisScript<Long> cancelStock() {
        return cancelStockScript;
    }
}


