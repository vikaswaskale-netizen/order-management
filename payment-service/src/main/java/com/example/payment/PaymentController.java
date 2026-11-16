package com.example.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentRepository payments;
    public PaymentController(PaymentRepository payments) { this.payments = payments; }

    // Simulation only. No card details or real payment provider are involved.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment pay(@Valid @RequestBody PaymentRequest request) {
        String status = request.paymentMethod().equals("DECLINE") ? "DECLINED" : "APPROVED";
        return payments.save(new Payment(request.orderId(), request.amount(), status));
    }

    @GetMapping
    public List<Payment> list() { return payments.findAll(); }

    @GetMapping("/{id}")
    public Payment get(@PathVariable Long id) {
        return payments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public record PaymentRequest(@NotNull @Positive Long orderId,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotBlank @Pattern(regexp = "CARD|DECLINE") String paymentMethod) {}
}
