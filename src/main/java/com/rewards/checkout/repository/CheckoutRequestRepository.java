package com.rewards.checkout.repository;

import com.rewards.checkout.domain.entity.CheckoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequest, String> {
}
