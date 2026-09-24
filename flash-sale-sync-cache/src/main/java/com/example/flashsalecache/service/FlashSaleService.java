package com.example.flashsalecache.service;

import com.example.flashsalecache.dto.ProductDTO;
import com.example.flashsalecache.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.example.flashsalecache.config.CacheNames.FLASH_SALE_CACHE;
import static com.example.flashsalecache.config.CacheNames.NO_SYNC_CACHE;

/**
 * BAI TAP 4 (SS17) - CHONG CACHE STAMPEDE BANG sync = true.
 *
 * Ten cache : "flash-sale"
 * Key        : #id
 *
 * Co che: khi TTL het han, hang nghin request cung luc nhan Cache Miss. Voi sync = true, Spring
 * se KHOA theo tung key lai: CHI MOT luong duoc chay vao than ham (va xuong Database), tat ca cac
 * luong con lai CHO va nhan lai ket qua tu cache vua duoc tao.
 */
@Service
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);

    private final ProductRepository productRepository;
    private final long queryDelayMs;

    public FlashSaleService(ProductRepository productRepository,
                            @Value("${app.db.query-delay-ms:2000}") long queryDelayMs) {
        this.productRepository = productRepository;
        this.queryDelayMs = queryDelayMs;
    }

    /**
     * PHUONG THUC CHINH CUA BAI: @Cacheable voi sync = true.
     * Chi mot luong duoc phep xuong Database cho moi key.
     */
    @Cacheable(value = FLASH_SALE_CACHE, key = "#id", sync = true)
    public ProductDTO getProduct(Long id) {
        log.info("Fetching from Database for product {}", id);
        simulateSlowQuery();
        return productRepository.findById(id)
                .map(ProductDTO::from)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay san pham id = " + id));
    }

    /**
     * BIEN THE DOI CHIEU: sync = false (dung de chung minh neu thieu sync thi Cache Stampede xay ra).
     * Dung cache rieng "flash-sale-no-sync" de khong lam nhieu ket qua cua phuong thuc chinh.
     */
    @Cacheable(value = NO_SYNC_CACHE, key = "#id", sync = false)
    public ProductDTO getProductWithoutSync(Long id) {
        log.info("Fetching from Database for product {}", id);
        simulateSlowQuery();
        return productRepository.findById(id)
                .map(ProductDTO::from)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay san pham id = " + id));
    }

    /** Mo phong truy van phuc tap: Thread.sleep(2000) theo dung yeu cau cua de bai. */
    private void simulateSlowQuery() {
        try {
            Thread.sleep(queryDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
