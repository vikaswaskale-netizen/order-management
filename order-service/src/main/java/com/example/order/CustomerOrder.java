package com.example.order;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String sku;
    public int quantity;
    public BigDecimal total;
    public Long paymentId;
    public String status;
    public String message;
    protected CustomerOrder() {}
    public CustomerOrder(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
        this.status = "PENDING";
    }
}
