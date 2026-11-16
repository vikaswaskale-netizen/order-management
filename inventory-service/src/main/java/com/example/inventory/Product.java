package com.example.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Product {
    @Id public String sku;
    public String name;
    public BigDecimal price;
    public int stock;

    protected Product() {}
    public Product(String sku, String name, BigDecimal price, int stock) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }
}
