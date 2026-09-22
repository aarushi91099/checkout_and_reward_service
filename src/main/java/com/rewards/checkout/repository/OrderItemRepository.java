package com.rewards.checkout.repository;

import com.rewards.checkout.domain.entity.OrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    @Query("select oi.productId as productId, oi.productName as productName, sum(oi.quantity) as quantitySold, "
            + "sum(oi.unitPrice * oi.quantity) as revenue from OrderItem oi group by oi.productId, oi.productName")
    List<ProductSalesSummary> aggregateByProduct();
}
