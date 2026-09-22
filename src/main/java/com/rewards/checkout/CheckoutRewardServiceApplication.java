package com.rewards.checkout;

import com.rewards.checkout.config.RewardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RewardProperties.class)
public class CheckoutRewardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutRewardServiceApplication.class, args);
    }
}
