package com.myroutine.product.infrastructure;

import com.myroutine.product.domain.StockGate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
public class RedisStockGate implements StockGate {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> stockGateScript;

    public RedisStockGate(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.stockGateScript = new DefaultRedisScript<>();
        this.stockGateScript.setLocation(new ClassPathResource("redis/stock-gate.lua"));
        this.stockGateScript.setResultType(Long.class);
    }

    @Override
    public int tryAcquire(List<UUID> productIds, List<Integer> quantities) {
        List<String> keys = productIds.stream().map(productId -> "stock:available:" + productId).toList();
        Object[] quantitiesAsString = quantities.stream().map(String::valueOf).toArray();
        Long execute = redisTemplate.execute(stockGateScript, keys, quantitiesAsString);
        return execute.intValue();
    }

    @Override
    public void giveBack(List<UUID> productIds, List<Integer> quantities) {
        for (int i = 0; i < productIds.size(); i++) {
            String key = "stock:available:" + productIds.get(i);
            Integer quantity = quantities.get(i);
            redisTemplate.opsForValue().increment(key, quantity);
        }
    }
}
