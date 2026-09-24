package com.example.flashsalecache;

import com.example.flashsalecache.dto.ProductDTO;
import com.example.flashsalecache.repository.ProductRepository;
import com.example.flashsalecache.service.FlashSaleService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Ban N request dong thoi vao cung mot id bang CompletableFuture (dung chung cho cac test). */
final class ConcurrentBurst {

    record Result(int success, int errors, int databaseQueries, long elapsedMs) {
    }

    private ConcurrentBurst() {
    }

    static Result fire(FlashSaleService service, ProductRepository repository, Long productId, int requests)
            throws Exception {
        repository.reset();
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        AtomicInteger errors = new AtomicInteger();
        long startedAt = System.nanoTime();
        try {
            List<CompletableFuture<ProductDTO>> futures = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return service.getProduct(productId);
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
            return new Result(success, errors.get(), repository.queryCount(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        } finally {
            pool.shutdownNow();
        }
    }
}
