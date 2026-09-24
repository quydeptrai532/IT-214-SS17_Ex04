package com.example.flashsalecache.controller;

import com.example.flashsalecache.dto.ProductDTO;
import com.example.flashsalecache.repository.ProductRepository;
import com.example.flashsalecache.service.FlashSaleService;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.flashsalecache.config.CacheNames.FLASH_SALE_CACHE;
import static com.example.flashsalecache.config.CacheNames.NO_SYNC_CACHE;

@RestController
@RequestMapping("/api/flash-sale")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;
    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    public FlashSaleController(FlashSaleService flashSaleService,
                               ProductRepository productRepository,
                               CacheManager cacheManager) {
        this.flashSaleService = flashSaleService;
        this.productRepository = productRepository;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(flashSaleService.getProduct(id));
    }

    @PostMapping("/products/{id}/evict")
    public ResponseEntity<String> evict(@PathVariable Long id) {
        cacheManager.getCache(FLASH_SALE_CACHE).evict(id);
        cacheManager.getCache(NO_SYNC_CACHE).evict(id);
        return ResponseEntity.ok("Da xoa cache cho id = " + id);
    }

    /**
     * Ban 50 request DONG THOI vao cung mot id bang CompletableFuture,
     * dung de kiem chung co che sync = true.
     */
    @GetMapping("/concurrent")
    public ResponseEntity<ConcurrentReport> concurrent(@RequestParam Long id,
                                                       @RequestParam(defaultValue = "50") int requests,
                                                       @RequestParam(defaultValue = "true") boolean sync) throws Exception {
        cacheManager.getCache(FLASH_SALE_CACHE).evict(id);
        cacheManager.getCache(NO_SYNC_CACHE).evict(id);
        productRepository.reset();

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        AtomicInteger errors = new AtomicInteger();
        long startedAt = System.nanoTime();
        List<CompletableFuture<ProductDTO>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requests; index++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return sync ? flashSaleService.getProduct(id) : flashSaleService.getProductWithoutSync(id);
                    } catch (RuntimeException exception) {
                        errors.incrementAndGet();
                        return null;
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            pool.shutdownNow();
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        return ResponseEntity.ok(new ConcurrentReport(requests, sync, productRepository.queryCount(),
                errors.get(), elapsedMs));
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<ConcurrentReport> diagnostics() {
        return ResponseEntity.ok(new ConcurrentReport(0, true, productRepository.queryCount(), 0, 0));
    }

    public record ConcurrentReport(int requests, boolean sync, int databaseQueries, int errors, long elapsedMs) {
    }
}
