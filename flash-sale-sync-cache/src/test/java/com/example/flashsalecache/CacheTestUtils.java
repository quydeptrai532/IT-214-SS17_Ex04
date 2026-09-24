package com.example.flashsalecache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Evict roi CHO den khi key thuc su bien mat (mot so provider ghi cache khong hien ket qua ngay). */
final class CacheTestUtils {

    private static final int MAX_ATTEMPTS = 50;
    private static final long DELAY_MS = 40;

    private CacheTestUtils() {
    }

    static void evictAndWait(CacheManager cacheManager, String cacheName, long id) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        cache.evict(id);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cache.get(id) == null) {
                return;
            }
            sleep(DELAY_MS);
        }
        throw new IllegalStateException("Khong the xoa key " + id + " khoi cache '" + cacheName + "'");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
