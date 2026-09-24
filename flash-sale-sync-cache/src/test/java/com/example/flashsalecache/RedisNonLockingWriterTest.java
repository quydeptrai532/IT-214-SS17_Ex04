package com.example.flashsalecache;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.flashsalecache.repository.ProductRepository;
import com.example.flashsalecache.service.FlashSaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;

import static com.example.flashsalecache.config.CacheNames.FLASH_SALE_CACHE;
import static com.example.flashsalecache.config.CacheNames.NO_SYNC_CACHE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * BANG CHUNG CHO CANH BAO TRONG DE BAI: "Redis khong ho tro sync mac dinh".
 *
 * Cung 50 request dong thoi, cung @Cacheable(sync = true), nhung cache writer o che do NON-LOCKING
 * (dung cach tao RedisCacheManager.builder(connectionFactory) thong thuong) thi khoa KHONG duoc tao
 * ra, va ca 50 luong deu xuong Database.
 *
 * => sync = true chi co tac dung khi Redis duoc cau hinh voi lockingRedisCacheWriter.
 */
@SpringBootTest(properties = {
        "app.cache.provider=redis",
        "app.cache.redis.locking-writer=false",
        "app.db.query-delay-ms=1000"
})
class RedisNonLockingWriterTest {

    private static final Long PRODUCT_ID = 1L;
    private static final int REQUESTS = 50;

    @Autowired
    private FlashSaleService flashSaleService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        CacheTestUtils.evictAndWait(cacheManager, FLASH_SALE_CACHE, PRODUCT_ID);
        CacheTestUtils.evictAndWait(cacheManager, NO_SYNC_CACHE, PRODUCT_ID);
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
    }

    private long fetchingLogCount() {
        return new ArrayList<>(appender.list).stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Fetching from Database for product"))
                .count();
    }

    @Test
    void redisVoiNonLockingWriter_syncTrueVanBiCacheStampede() throws Exception {
        ConcurrentBurst.Result result = ConcurrentBurst.fire(flashSaleService, productRepository, PRODUCT_ID, REQUESTS);
        long fetchingLogs = fetchingLogCount();

        System.out.println(">>> [KB5 REDIS + non-locking writer] " + REQUESTS + " request dong thoi");
        System.out.println(">>> [KB5 REDIS + non-locking writer] So dong log 'Fetching from Database' = " + fetchingLogs);
        System.out.println(">>> [KB5 REDIS + non-locking writer] So truy van DB = " + result.databaseQueries()
                + " -> sync = true KHONG co tac dung voi non-locking writer");

        assertThat(fetchingLogs)
                .as("Non-locking writer thi khoa khong duoc tao -> nhieu luong cung xuong DB")
                .isGreaterThan(1);
        assertThat(result.databaseQueries()).isGreaterThan(1);
    }
}
