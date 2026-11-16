package com.example.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final WebClient inventory;
    private final WebClient payment;

    public OrderService(OrderRepository orders, WebClient.Builder builder,
                        @Value("${services.inventory-url}") String inventoryUrl,
                        @Value("${services.payment-url}") String paymentUrl) {
        this.orders = orders;
        this.inventory = builder.clone().baseUrl(inventoryUrl).build();
        this.payment = builder.clone().baseUrl(paymentUrl).build();
    }

    // Each repository save is a local transaction. HTTP calls are outside DB transactions.
    public CustomerOrder place(OrderController.OrderRequest request) {
        CustomerOrder order = orders.save(new CustomerOrder(request.sku(), request.quantity()));
        Availability available;
        try {
            available = Objects.requireNonNull(inventory.get()
                    .uri("/api/products/{sku}/availability?quantity={quantity}", request.sku(), request.quantity())
                    .retrieve().bodyToMono(Availability.class).block(Duration.ofSeconds(5)));
        } catch (WebClientResponseException.NotFound e) {
            return finish(order, "REJECTED", "Product not found");
        } catch (RuntimeException e) {
            return finish(order, "INVENTORY_UNAVAILABLE", "Inventory could not be checked");
        }
        if (!available.available()) {
            return finish(order, "REJECTED", "Insufficient stock");
        }
        order.total = available.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        order.status = "PAYMENT_PENDING";
        orders.save(order);
        try {
            PaymentResult result = Objects.requireNonNull(payment.post().uri("/api/payments")
                    .bodyValue(new PaymentRequest(order.id, order.total, request.paymentMethod()))
                    .retrieve().bodyToMono(PaymentResult.class).block(Duration.ofSeconds(5)));
            order.paymentId = result.id();
            if ("APPROVED".equals(result.status())) {
                return finish(order, "CONFIRMED", "Payment approved");
            }
            if ("DECLINED".equals(result.status())) {
                return finish(order, "PAYMENT_DECLINED", "Payment declined");
            }
            return finish(order, "PAYMENT_UNKNOWN", "Unexpected payment response; check payment service");
        } catch (RuntimeException e) {
            // A timeout does not prove that the remote payment failed. Never retry blindly.
            return finish(order, "PAYMENT_UNKNOWN", "Payment outcome unknown; check payment service before retrying");
        }
    }

    private CustomerOrder finish(CustomerOrder order, String status, String message) {
        order.status = status;
        order.message = message;
        return orders.save(order);
    }

    record Availability(String sku, BigDecimal unitPrice, int stock, boolean available) {}
    record PaymentRequest(Long orderId, BigDecimal amount, String paymentMethod) {}
    record PaymentResult(Long id, Long orderId, BigDecimal amount, String status) {}
}
