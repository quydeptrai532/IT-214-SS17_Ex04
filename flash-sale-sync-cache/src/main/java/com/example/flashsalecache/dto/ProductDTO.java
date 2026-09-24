package com.example.flashsalecache.dto;

import com.example.flashsalecache.model.Product;

import java.io.Serializable;

public record ProductDTO(Long id, String name, long price, int stock) implements Serializable {

    public static ProductDTO from(Product product) {
        return new ProductDTO(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
