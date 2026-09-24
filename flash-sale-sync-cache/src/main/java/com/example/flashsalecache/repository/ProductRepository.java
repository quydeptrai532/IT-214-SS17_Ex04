package com.example.flashsalecache.repository;

import com.example.flashsalecache.model.Product;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Database mo phong. Dem so lan thuc su bi truy van -> dung lam bang chung dinh luong. */
@Repository
public class ProductRepository {

    private final Map<Long, Product> database = new ConcurrentHashMap<>();
    private final AtomicInteger queryCount = new AtomicInteger();

    public ProductRepository() {
        database.put(1L, new Product(1L, "iPhone 15 Pro Max - Flash Sale", 19_990_000L, 100));
        database.put(2L, new Product(2L, "Samsung Galaxy S24 Ultra - Flash Sale", 18_990_000L, 80));
        database.put(3L, new Product(3L, "MacBook Pro M3 - Flash Sale", 39_990_000L, 40));
    }

    public Optional<Product> findById(Long id) {
        queryCount.incrementAndGet();
        return Optional.ofNullable(database.get(id)).map(Product::copy);
    }

    public int queryCount() {
        return queryCount.get();
    }

    public void reset() {
        queryCount.set(0);
    }
}
