package com.rewards.checkout.repository;

import com.rewards.checkout.domain.entity.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select coalesce(sum(o.subtotal), 0) as grossRevenue, coalesce(sum(o.discount), 0) as discounts, "
            + "coalesce(sum(o.total), 0) as netRevenue from Order o")
    OrderTotals sumTotals();
}
