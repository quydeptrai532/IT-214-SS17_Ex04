# BÀI TẬP 4 (SS17) — CHỐNG CACHE STAMPEDE VỚI `sync = true`

Xem phân tích tại `Ex04_Analysis.md`, bằng chứng chạy test tại `Ex04_TestEvidence.txt`.

## Tóm tắt

Bắn **50 request đồng thời** vào cùng một sản phẩm bằng `CompletableFuture`, khi cache đã hết hạn:

| Kịch bản | Số dòng log "Fetching from Database" | Số truy vấn DB | Thời gian |
|---|---|---|---|
| **`sync = true`** | **1** | **1** | **2018 ms** |
| `sync = false` | 50 | 50 | 2075 ms |

50 request hoàn thành trong **2008 ms** ≈ 1 lần tái tạo cache (2000 ms), không phải 50 × 2000 ms.

```java
@Cacheable(value = "flash-sale", key = "#id", sync = true)
public ProductDTO getProduct(Long id) {
    log.info("Fetching from Database for product {}", id);
    Thread.sleep(2000);                       // mô phỏng truy vấn phức tạp
    return productRepository.findById(id)...;
}
```

## Điểm đáng chú ý: lưu ý của đề về Redis là ĐÚNG, và có cách khắc phục

Đề ghi *"Redis không hỗ trợ sync mặc định, cần dùng Caffeine hoặc Ehcache"*. Bài làm đã kiểm chứng:

| Cách cấu hình Redis | 50 request đồng thời |
|---|---|
| `RedisCacheManager.builder(connectionFactory)` (mặc định) | **50 truy vấn DB** → sync vô dụng |
| `RedisCacheWriter.lockingRedisCacheWriter(connectionFactory)` | **1 truy vấn DB** → sync hoạt động |

Vì vậy bài làm dùng **Caffeine** làm provider mặc định (chạy đúng ngay), kèm **profile Redis** đã sửa
bằng locking writer để đối chiếu và dùng khi cần nhiều instance.

## Cấu trúc

```
Ex04/
├── Ex04_Analysis.md
├── Ex04_TestEvidence.txt
├── README.md
└── flash-sale-sync-cache/
    └── src/main/java/com/example/flashsalecache/
        ├── config/CacheNames.java          # "flash-sale" + cache đối chiếu
        ├── config/CaffeineCacheConfig.java # provider mặc định (khóa theo key sẵn)
        ├── config/RedisCacheConfig.java    # provider đối chiếu + locking writer
        ├── service/FlashSaleService.java   # ★ @Cacheable(sync = true) + bản sync = false
        ├── repository/ProductRepository.java
        └── controller/FlashSaleController.java  # endpoint bắn 50 request đồng thời
```

## Chạy test

```bash
cd flash-sale-sync-cache
./gradlew test          # Windows: gradlew.bat test     -> 5/5 PASSED
```

## Chạy thật

```bash
./gradlew bootRun       # port 8600

curl -X POST http://localhost:8600/api/flash-sale/products/1/evict
curl "http://localhost:8600/api/flash-sale/concurrent?id=1&requests=50&sync=true"
curl "http://localhost:8600/api/flash-sale/concurrent?id=1&requests=50&sync=false"
```

Để lấy ảnh chụp màn hình nộp bài: chạy `bootRun`, gọi endpoint `concurrent` ở trên rồi chụp console —
sẽ thấy **đúng 1 dòng** `Fetching from Database for product 1`.

**5/5 test PASSED — BUILD SUCCESSFUL in 53s**
