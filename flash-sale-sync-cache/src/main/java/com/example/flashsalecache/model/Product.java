package com.example.flashsalecache.model;

public class Product {

    private final Long id;
    private final String name;
    private final long price;
    private final int stock;

    public Product(Long id, String name, long price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public Product copy() {
        return new Product(id, name, price, stock);
    }
}
