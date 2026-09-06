package com.example.weather.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisLockServiceImpl implements RedisLockService {

    private final StringRedisTemplate redisTemplate;

    public RedisLockServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, long ttlSeconds) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        Boolean success = ops.setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }
}
