package com.example.flashsalecache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Provider doi chieu: REDIS.
 *
 * BAT BUOC phai dung RedisCacheWriter.lockingRedisCacheWriter(...) thi @Cacheable(sync = true)
 * moi thuc su khoa cac luong lai. Neu dung RedisCacheManager.builder(connectionFactory)
 * (mac dinh la non-locking writer), sync = true se KHONG co tac dung - da kiem chung bang test
 * RedisSyncCacheTest.nonLockingWriter_syncKhongCoTacDung.
 *
 * Bat provider nay bang: app.cache.provider=redis
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.provider", havingValue = "redis")
public class RedisCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${app.cache.ttl:5m}") Duration ttl,
                                     @Value("${app.cache.redis.locking-writer:true}") boolean lockingWriter) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("flashsale:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder()
                                .enableUnsafeDefaultTyping()
                                .build()));

        RedisCacheWriter cacheWriter = lockingWriter
                ? RedisCacheWriter.lockingRedisCacheWriter(connectionFactory)
                : RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        log.info("[RedisCacheConfig] Khoi tao RedisCacheManager | lockingWriter={}", lockingWriter);

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(configuration)
                .withCacheConfiguration(CacheNames.FLASH_SALE_CACHE, configuration)
                .withCacheConfiguration(CacheNames.NO_SYNC_CACHE, configuration)
                .build();
    }

    /** Fail-open: loi Redis khong duoc lam sap nghiep vu doc. */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[CacheFallback] Redis loi khi doc cache '{}' (key={}) -> doc thang DB: {}",
                        cache.getName(), key, exception.getClass().getSimpleName());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("[CacheFallback] Ghi cache '{}' that bai (key={}) -> bo qua.", cache.getName(), key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[CacheFallback] Xoa cache '{}' that bai (key={}) -> bo qua.", cache.getName(), key);
            }
        };
    }
}
