package com.example.order;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Real HTTP calls to small downstream stubs: no inventory/payment JVMs needed for this test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowTest {
    static final AtomicInteger paymentCalls = new AtomicInteger();
    static final HttpServer downstream = startDownstream();
    @Autowired TestRestTemplate rest;

    static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/products/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                int code = path.contains("MISSING") ? 404 : path.contains("BROKEN") ? 503 : 200;
                String json = "{\"sku\":\"MOUSE\",\"unitPrice\":25.00,\"stock\":50,\"available\":"
                        + !path.contains("KEYBOARD") + "}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/api/payments", exchange -> {
                paymentCalls.incrementAndGet();
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String status = request.contains("DECLINE") ? "DECLINED" : "APPROVED";
                byte[] body = ("{\"id\":42,\"status\":\"" + status + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(request.contains("75.00") ? 503 : 201, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + downstream.getAddress().getPort();
        registry.add("services.inventory-url", () -> url);
        registry.add("services.payment-url", () -> url);
    }

    @AfterAll static void stop() { downstream.stop(0); }

    @Test void confirmsAndPersistsOrderUsingInventoryPrice() {
        var response = rest.postForEntity("/api/orders", request("MOUSE", 2, "CARD"), CustomerOrder.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        CustomerOrder order = response.getBody();
        assertThat(order).isNotNull();
        assertThat(order.status).isEqualTo("CONFIRMED");
        assertThat(order.total).isEqualByComparingTo("50.00");
        assertThat(order.paymentId).isEqualTo(42L);
        assertThat(rest.getForObject("/api/orders/" + order.id, CustomerOrder.class).status).isEqualTo("CONFIRMED");
    }

    @Test void recordsDeclinedPayment() {
        assertThat(rest.postForObject("/api/orders", request("MOUSE", 1, "DECLINE"), CustomerOrder.class).status)
                .isEqualTo("PAYMENT_DECLINED");
    }

    @Test void rejectsUnavailableOrMissingProductWithoutCallingPayment() {
        int before = paymentCalls.get();
        for (String sku : new String[]{"KEYBOARD", "MISSING"}) {
            assertThat(rest.postForObject("/api/orders", request(sku, 1, "CARD"), CustomerOrder.class).status)
                    .isEqualTo("REJECTED");
        }
        assertThat(paymentCalls.get()).isEqualTo(before);
    }

    @Test void recordsInventoryFailure() {
        assertThat(rest.postForObject("/api/orders", request("BROKEN", 1, "CARD"), CustomerOrder.class).status)
                .isEqualTo("INVENTORY_UNAVAILABLE");
    }

    @Test void preservesUncertainPaymentOutcome() {
        assertThat(rest.postForObject("/api/orders", request("MOUSE", 3, "CARD"), CustomerOrder.class).status)
                .isEqualTo("PAYMENT_UNKNOWN");
    }

    @Test void rejectsInvalidRequest() {
        assertThat(rest.postForEntity("/api/orders", request("MOUSE", 0, "CARD"), String.class)
                .getStatusCode().value()).isEqualTo(400);
    }

    private Map<String, Object> request(String sku, int quantity, String method) {
        return Map.of("sku", sku, "quantity", quantity, "paymentMethod", method);
    }
}
