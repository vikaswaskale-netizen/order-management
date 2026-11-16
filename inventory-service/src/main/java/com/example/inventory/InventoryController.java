package com.example.inventory;

import jakarta.validation.constraints.Min;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class InventoryController {
    private final ProductRepository products;
    public InventoryController(ProductRepository products) { this.products = products; }

    @Bean
    CommandLineRunner seedProducts() {
        return args -> products.saveAll(List.of(
                new Product("LAPTOP", "Learning laptop", new BigDecimal("899.00"), 10),
                new Product("MOUSE", "Wireless mouse", new BigDecimal("25.00"), 50),
                new Product("KEYBOARD", "Mechanical keyboard", new BigDecimal("75.00"), 0)));
    }

    @GetMapping
    public List<Product> list() { return products.findAll(); }

    // Deliberately checks stock only: reserving stock is a later learning exercise.
    @GetMapping("/{sku}/availability")
    public Availability check(@PathVariable String sku, @RequestParam @Min(1) int quantity) {
        Product product = products.findById(sku).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + sku));
        return new Availability(sku, product.price, product.stock, product.stock >= quantity);
    }

    public record Availability(String sku, BigDecimal unitPrice, int stock, boolean available) {}
}
