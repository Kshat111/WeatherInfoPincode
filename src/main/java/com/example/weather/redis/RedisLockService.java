package com.example.weather.redis;

public interface RedisLockService {
    boolean tryLock(String key, long ttlSeconds);
    void releaseLock(String key);
}
