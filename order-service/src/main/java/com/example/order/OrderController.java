package com.example.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final OrderRepository orders;
    public OrderController(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrder place(@Valid @RequestBody OrderRequest request) { return service.place(request); }

    @GetMapping
    public List<CustomerOrder> list() { return orders.findAll(); }

    @GetMapping("/{id}")
    public CustomerOrder get(@PathVariable Long id) {
        return orders.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    public record OrderRequest(@NotBlank String sku, @Min(1) int quantity,
                               @NotBlank @Pattern(regexp = "CARD|DECLINE") String paymentMethod) {}
}
