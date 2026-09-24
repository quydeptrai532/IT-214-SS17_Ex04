package com.example.flashsalecache;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.flashsalecache.dto.ProductDTO;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.flashsalecache.config.CacheNames.FLASH_SALE_CACHE;
import static com.example.flashsalecache.config.CacheNames.NO_SYNC_CACHE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * BAI TAP 4 (SS17) - 50 request dong thoi vao cung mot id, do bang CompletableFuture.
 *
 * Cache provider mac dinh: CAFFEINE (ho tro khoa theo key san).
 */
@SpringBootTest
class FlashSaleSyncCacheTest {

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
        productRepository.reset();
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

    /** Dem so dong log "Fetching from Database for product X" - dung tieu chi cham diem cua de bai. */
    private long fetchingLogCount() {
        return new ArrayList<>(appender.list).stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Fetching from Database for product"))
                .count();
    }

    private BurstResult fireConcurrentRequests(boolean sync, int requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        AtomicInteger errors = new AtomicInteger();
        long startedAt = System.nanoTime();
        try {
            List<CompletableFuture<ProductDTO>> futures = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return sync ? flashSaleService.getProduct(PRODUCT_ID)
                                : flashSaleService.getProductWithoutSync(PRODUCT_ID);
                    } catch (RuntimeException exception) {
                        errors.incrementAndGet();
                        return null;
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            int success = 0;
            for (CompletableFuture<ProductDTO> future : futures) {
                if (future.join() != null) {
                    success++;
                }
            }
            return new BurstResult(success, errors.get(), (System.nanoTime() - startedAt) / 1_000_000);
        } finally {
            pool.shutdownNow();
        }
    }

    private record BurstResult(int success, int errors, long elapsedMs) {
    }

    @Test
    void syncTrue_50RequestDongThoi_chiMotDongFetchingFromDatabase() throws Exception {
        BurstResult result = fireConcurrentRequests(true, REQUESTS);
        long fetchingLogs = fetchingLogCount();

        System.out.println(">>> [KB1 sync = true] " + REQUESTS + " request dong thoi vao id=" + PRODUCT_ID);
        System.out.println(">>> [KB1 sync = true] So dong log 'Fetching from Database for product' = " + fetchingLogs);
        System.out.println(">>> [KB1 sync = true] So truy van DB = " + productRepository.queryCount()
                + " | thanh cong = " + result.success() + "/" + REQUESTS
                + " | loi = " + result.errors()
                + " | tong thoi gian = " + result.elapsedMs() + " ms");

        assertThat(fetchingLogs).as("Chi duoc co DUNG 1 dong log Fetching from Database").isEqualTo(1);
        assertThat(productRepository.queryCount()).isEqualTo(1);
        assertThat(result.errors()).as("Khong request nao bi timeout/loi").isZero();
        assertThat(result.success()).isEqualTo(REQUESTS);
    }

    @Test
    void tongThoiGianXapXiThoiGianTaiTaoCache_chuKhongPhai50Lan() throws Exception {
        BurstResult result = fireConcurrentRequests(true, REQUESTS);

        System.out.println(">>> [KB2 THOI GIAN] " + REQUESTS + " request hoan thanh trong "
                + result.elapsedMs() + " ms (thoi gian tai tao cache = 2000 ms;"
                + " neu tuan tu se la " + (REQUESTS * 2000) + " ms)");

        assertThat(result.elapsedMs())
                .as("Tong thoi gian phai xap xi 1 lan tai tao cache (2s) + overhead")
                .isLessThan(4000L);
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(2000L);
    }

    @Test
    void syncFalse_voiCung50Request_thiRatNhieuDongFetchingFromDatabase() throws Exception {
        BurstResult result = fireConcurrentRequests(false, REQUESTS);
        long fetchingLogs = fetchingLogCount();

        System.out.println(">>> [KB3 sync = false] " + REQUESTS + " request dong thoi vao id=" + PRODUCT_ID);
        System.out.println(">>> [KB3 sync = false] So dong log 'Fetching from Database for product' = " + fetchingLogs);
        System.out.println(">>> [KB3 sync = false] So truy van DB = " + productRepository.queryCount()
                + " | tong thoi gian = " + result.elapsedMs() + " ms");

        assertThat(fetchingLogs)
                .as("Khong co sync thi nhieu luong cung xuong DB - day chinh la Cache Stampede")
                .isGreaterThan(1);
        assertThat(productRepository.queryCount()).isGreaterThan(1);
    }
}
