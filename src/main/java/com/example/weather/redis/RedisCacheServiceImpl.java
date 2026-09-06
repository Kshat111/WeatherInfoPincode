package com.example.weather.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisCacheServiceImpl implements RedisCacheService {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void put(String key, String value, long ttlSeconds) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
