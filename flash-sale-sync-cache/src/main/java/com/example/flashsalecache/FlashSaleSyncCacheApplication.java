package com.example.flashsalecache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FlashSaleSyncCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashSaleSyncCacheApplication.class, args);
    }
}
