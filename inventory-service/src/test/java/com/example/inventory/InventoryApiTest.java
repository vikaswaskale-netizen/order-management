package com.example.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryApiTest {
    @Autowired TestRestTemplate rest;

    @Test void checksSeededStockAndPrice() {
        var available = rest.getForObject("/api/products/MOUSE/availability?quantity=2",
                InventoryController.Availability.class);
        assertThat(available.available()).isTrue();
        assertThat(available.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(rest.getForObject("/api/products/KEYBOARD/availability?quantity=1",
                InventoryController.Availability.class).available()).isFalse();
    }

    @Test void rejectsMissingProductAndInvalidQuantity() {
        assertThat(rest.getForEntity("/api/products/UNKNOWN/availability?quantity=1", String.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(rest.getForEntity("/api/products/MOUSE/availability?quantity=0", String.class)
                .getStatusCode().value()).isEqualTo(400);
    }
}
