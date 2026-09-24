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
 * DOI CHIEU: dung REDIS lam cache provider, co LOCKING cache writer.
 *
 * De bai luu y "Redis khong ho tro sync mac dinh, can dung Caffeine hoac Ehcache".
 * Dieu do dung voi cach cau hinh mac dinh, nhung CO the khac phuc bang
 * RedisCacheWriter.lockingRedisCacheWriter(...) - xem RedisNonLockingWriterTest de so sanh.
 */
@SpringBootTest(properties = {
        "app.cache.provider=redis",
        "app.cache.redis.locking-writer=true",
        "app.db.query-delay-ms=1000"
})
class RedisSyncCacheTest {

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
    void redisVoiLockingWriter_50RequestDongThoi_chiMotDongFetching() throws Exception {
        ConcurrentBurst.Result result = ConcurrentBurst.fire(flashSaleService, productRepository, PRODUCT_ID, REQUESTS);
        long fetchingLogs = fetchingLogCount();

        System.out.println(">>> [KB4 REDIS + locking writer] " + REQUESTS + " request dong thoi");
        System.out.println(">>> [KB4 REDIS + locking writer] So dong log 'Fetching from Database' = " + fetchingLogs);
        System.out.println(">>> [KB4 REDIS + locking writer] So truy van DB = " + result.databaseQueries()
                + " | thanh cong = " + result.success() + "/" + REQUESTS
                + " | loi = " + result.errors()
                + " | thoi gian = " + result.elapsedMs() + " ms");

        assertThat(fetchingLogs).isEqualTo(1);
        assertThat(result.databaseQueries()).isEqualTo(1);
        assertThat(result.errors()).isZero();
        assertThat(result.success()).isEqualTo(REQUESTS);
    }
}
