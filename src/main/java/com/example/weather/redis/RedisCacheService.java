package com.example.weather.redis;

public interface RedisCacheService {
    String get(String key);
    void put(String key, String value, long ttlSeconds);
    void delete(String key);
}
