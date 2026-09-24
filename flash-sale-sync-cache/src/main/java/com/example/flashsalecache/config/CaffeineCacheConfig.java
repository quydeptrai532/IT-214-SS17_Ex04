package com.example.flashsalecache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Provider mac dinh: CAFFEINE (cache trong bo nho cua JVM).
 *
 * Vi sao chon Caffeine cho bai toan chong Cache Stampede?
 * Caffeine cai dat san co che khoa theo tung key: khi nhieu luong cung yeu cau mot key dang duoc
 * tai, CHI MOT luong thuc thi ham nap du lieu, cac luong con lai cho va dung lai ket qua.
 * Nho vay @Cacheable(sync = true) hoat dong dung ngay, khong can cau hinh gi them.
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineCacheConfig {

    @Bean
    public CacheManager cacheManager(@Value("${app.cache.ttl:5m}") Duration ttl,
                                     @Value("${app.cache.max-size:1000}") long maxSize) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                CacheNames.FLASH_SALE_CACHE, CacheNames.NO_SYNC_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize));
        return cacheManager;
    }
}
