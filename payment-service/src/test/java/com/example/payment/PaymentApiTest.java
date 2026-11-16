package com.example.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiTest {
    @Autowired TestRestTemplate rest;

    @Test void persistsApprovalAndDecline() {
        for (String method : new String[]{"CARD", "DECLINE"}) {
            var response = rest.postForEntity("/api/payments",
                    Map.of("orderId", 1, "amount", 25, "paymentMethod", method), Payment.class);
            assertThat(response.getStatusCode().value()).isEqualTo(201);
            Payment payment = response.getBody();
            assertThat(payment).isNotNull();
            assertThat(payment.status).isEqualTo(method.equals("CARD") ? "APPROVED" : "DECLINED");
            assertThat(rest.getForObject("/api/payments/" + payment.id, Payment.class).amount)
                    .isEqualByComparingTo("25.00");
        }
    }

    @Test void rejectsInvalidPayment() {
        assertThat(rest.postForEntity("/api/payments",
                Map.of("orderId", 1, "amount", -1, "paymentMethod", "CARD"), String.class)
                .getStatusCode().value()).isEqualTo(400);
    }
}
