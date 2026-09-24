# BÀI TẬP 4 (SS17) — CHỐNG CACHE STAMPEDE VỚI THUỘC TÍNH SYNC

## 1. BẢN CHẤT CỦA CACHE STAMPEDE

Cache được cài TTL (ví dụ 5 phút). Khi TTL vừa hết hạn, **hàng nghìn request cùng lúc** truy cập vào
cùng một key và **cùng nhận Cache Miss**. Nếu mọi request đều tự xuống Database để tái tạo cache:

```
        TTL het han
             │
    ┌────────┼────────┬────────┬────────┐
    ▼        ▼        ▼        ▼        ▼
  Req 1   Req 2    Req 3    Req 4  ... Req 50        ← tất cả đều MISS
    │        │        │        │        │
    ▼        ▼        ▼        ▼        ▼
  ┌────────────────────────────────────────────┐
  │              DATABASE                       │   ← bị 50 truy vấn cùng lúc
  │  (cạn connection pool -> SẬP)               │
  └────────────────────────────────────────────┘
```

Hiện tượng này gọi là **Cache Stampede** (còn gọi là **Thundering Herd**). Hậu quả: Database quá tải,
cạn kiệt connection pool, sập toàn bộ hệ thống — đúng lúc đông khách nhất.

**Nguyên nhân gốc:** không có ai "đứng ra chịu trách nhiệm" tái tạo cache. 50 request độc lập, mỗi request
tự quyết định đi lấy dữ liệu.

## 2. CƠ CHẾ KHÓA CỦA `sync = true`

```java
@Cacheable(value = "flash-sale", key = "#id", sync = true)
public ProductDTO getProduct(Long id) {
    log.info("Fetching from Database for product {}", id);
    Thread.sleep(2000);                     // mô phỏng truy vấn phức tạp
    return productRepository.findById(id)...;
}
```

Khi `sync = true`, Spring **không để mọi luồng cùng chạy vào thân hàm**. Nó dùng cơ chế
`Cache.get(key, Callable)` — tức là "khóa theo từng key":

```
    ┌────────┼────────┬────────┬────────┐
    ▼        ▼        ▼        ▼        ▼
  Req 1   Req 2    Req 3    Req 4  ... Req 50
    │        │        │        │        │
    │        └────────┴────────┴────────┘
    │                     │
    │              CHỜ (không xuống DB)
    ▼                     │
  giữ KHÓA               │
    │                     │
    ▼                     │
  DATABASE (1 truy vấn)   │
    │                     │
    ▼                     │
  ghi vào cache, nhả khóa │
    │                     │
    └─────────────────────┘
              │
              ▼
     cả 50 request nhận kết quả
```

Điểm mấu chốt: **khóa nằm ở tầng cache, không phải ở tầng service**. Vì vậy nó hoạt động với mọi luồng
trong cùng tiến trình, không cần viết `synchronized` hay `Lock` thủ công.

## 3. KẾT QUẢ ĐO ĐƯỢC

Chạy 50 request đồng thời bằng `CompletableFuture` vào cùng `id = 1`:

| Kịch bản | Số dòng log "Fetching from Database" | Số truy vấn DB | Thời gian | Lỗi |
|---|---|---|---|---|
| **`sync = true`** (Caffeine) | **1** | **1** | **2018 ms** | 0 |
| `sync = false` (Caffeine) | 50 | 50 | 2075 ms | 0 |
| `sync = true` (Redis + locking writer) | **1** | **1** | 3856 ms | 0 |
| `sync = true` (Redis + non-locking writer) | 50 | 50 | — | 0 |

**Về tiêu chí thời gian:** 50 request hoàn thành trong **2008 ms** ≈ thời gian tái tạo cache (2000 ms)
cộng chút overhead. Nếu phải chạy tuần tự sẽ là **50 × 2000 = 100.000 ms**. Con số này chứng minh
49 request còn lại đã **chờ** và dùng lại kết quả của request đầu tiên.

> Test đếm **trực tiếp số dòng log** bằng Logback `ListAppender` (gắn vào root logger), chứ không đếm
> qua một biến đếm nội bộ — đúng theo tiêu chí chấm điểm "trong log chỉ xuất hiện duy nhất 1 dòng".

## 4. ⚠️ LƯU Ý CỦA ĐỀ VỀ REDIS — KIỂM CHỨNG VÀ CÁCH KHẮC PHỤC

Đề bài ghi:

> *"Nếu chương trình bị treo, kiểm tra xem cache provider có hỗ trợ lock không (ví dụ: **Redis không hỗ
> trợ sync mặc định**, cần dùng Caffeine hoặc Ehcache)."*

**Nhận định này đúng**, và bài làm đã kiểm chứng bằng test cụ thể:

| Cách cấu hình Redis | 50 request đồng thời | Kết luận |
|---|---|---|
| `RedisCacheManager.builder(connectionFactory)` (mặc định) | **50 truy vấn DB** | `sync = true` **không có tác dụng** |
| `RedisCacheWriter.lockingRedisCacheWriter(connectionFactory)` | **1 truy vấn DB** | `sync = true` **hoạt động đúng** |

**Nguyên nhân:** `RedisCacheManager.builder(connectionFactory)` dùng `nonLockingRedisCacheWriter` —
nghĩa là **không có khóa phân tán nào được tạo ra trong Redis**. Khi đó `sync = true` không gom được
các luồng đang chờ.

**Cách khắc phục:** buộc phải dùng **locking** cache writer:

```java
RedisCacheWriter cacheWriter = RedisCacheWriter.lockingRedisCacheWriter(connectionFactory);
return RedisCacheManager.builder(cacheWriter)...;
```

Lợi ích kèm theo: khóa nằm **trong Redis** nên đúng cho **cả nhiều instance** của ứng dụng (khoá cục bộ
trong JVM không làm được điều này).

### Vậy bài làm chọn provider nào?

- **Mặc định: Caffeine** (`app.cache.provider=caffeine`) — cache trong bộ nhớ, hỗ trợ khóa theo key sẵn,
  `sync = true` chạy đúng ngay không cần cấu hình gì. Phù hợp với bài toán đa luồng trong một tiến trình.
- **Kèm theo: profile Redis** (`app.cache.provider=redis`) đã được sửa bằng locking writer, để đối chiếu
  và để dùng khi triển khai nhiều instance.

Cả hai đều có test chứng minh.

## 5. KIỂM THỬ ĐA LUỒNG

Bài làm dùng **`CompletableFuture`** (đề cho phép chọn JMeter hoặc CompletableFuture):

```java
ExecutorService pool = Executors.newFixedThreadPool(50);
List<CompletableFuture<ProductDTO>> futures = new ArrayList<>();
for (int i = 0; i < 50; i++) {
    futures.add(CompletableFuture.supplyAsync(() -> flashSaleService.getProduct(1L), pool));
}
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

Có sẵn endpoint để chạy trực tiếp từ trình duyệt/Postman mà không cần JMeter:

```
GET http://localhost:8600/api/flash-sale/concurrent?id=1&requests=50&sync=true
GET http://localhost:8600/api/flash-sale/concurrent?id=1&requests=50&sync=false
```

> **Ghi chú về JMeter:** nếu giảng viên yêu cầu JMeter, cấu hình như sau — Thread Group: *Number of
> Threads* = 50, *Ramp-up period* = 0 (để các request thực sự đồng thời), *Loop Count* = 1; HTTP Request
> `GET http://localhost:8600/api/flash-sale/products/1`. Nhớ gọi
> `POST /api/flash-sale/products/1/evict` trước để cache trống, nếu không sẽ không đo được gì.

## 6. HƯỚNG DẪN CHẠY

```bash
cd Session17/Ex04/flash-sale-sync-cache
./gradlew test          # Windows: gradlew.bat test     -> 5/5 PASSED

./gradlew bootRun       # port 8600 (provider Caffeine)

# Doi chieu voi Redis (can Redis dang chay o localhost:6379):
./gradlew bootRun --args='--app.cache.provider=redis'

# Xoa cache roi ban 50 request dong thoi:
curl -X POST http://localhost:8600/api/flash-sale/products/1/evict
curl "http://localhost:8600/api/flash-sale/concurrent?id=1&requests=50&sync=true"
```

## 7. KẾT QUẢ CHẠY THỬ

**5/5 test PASSED, BUILD SUCCESSFUL.** Chi tiết ở `Ex04_TestEvidence.txt`.

| Tiêu chí của đề | Kết quả |
|---|---|
| Đúng annotation | `@Cacheable(value = "flash-sale", key = "#id", sync = true)` |
| Một lần truy vấn DB | **1** dòng log "Fetching from Database for product 1" cho 50 request |
| Không bị timeout | 50/50 request thành công, 0 lỗi |
| Cơ chế lock hoạt động | Bỏ sync → 50 dòng log, 50 truy vấn DB |
| Thời gian xử lý | 2008 ms (thay vì 100.000 ms nếu tuần tự) |
