package com.rewards.checkout.integration.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rewards.checkout.integration.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReportReconciliationIT extends AbstractIntegrationTest {

    @Test
    void reportTotalsReconcileWithOrdersAndDoNotMutateState() {
        UUID productId = seedProduct("Widget", "10.00", 100);
        seedCoupon("REPORT10", "10", 9003L);

        UUID cartA = createCart();
        addItem(cartA, productId, 2);
        checkout(cartA, "key-" + UUID.randomUUID(), null);

        UUID cartB = createCart();
        addItem(cartB, productId, 3);
        checkout(cartB, "key-" + UUID.randomUUID(), "REPORT10");

        ResponseEntity<String> reportResponse = restTemplate.getForEntity(baseUrl() + "/admin/reports", String.class);
        assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode report = readJson(reportResponse);

        assertThat(report.get("totalOrders").asLong()).isEqualTo(2);
        assertThat(report.get("grossRevenue").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(report.get("discounts").decimalValue()).isEqualByComparingTo("3.00");
        assertThat(report.get("netRevenue").decimalValue()).isEqualByComparingTo("47.00");
        assertThat(report.get("couponSummary").get("totalGenerated").asLong()).isEqualTo(1);
        assertThat(report.get("couponSummary").get("totalRedeemed").asLong()).isEqualTo(1);

        Integer ordersBefore = jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
        restTemplate.getForEntity(baseUrl() + "/admin/reports", String.class);
        Integer ordersAfter = jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
        assertThat(ordersAfter).isEqualTo(ordersBefore);
    }
}
