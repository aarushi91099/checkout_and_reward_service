package com.rewards.checkout.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reward")
public record RewardProperties(int everyNthOrder, BigDecimal discountPercent) {
}
