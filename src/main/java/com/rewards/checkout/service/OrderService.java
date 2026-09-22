package com.rewards.checkout.service;

import com.rewards.checkout.domain.entity.Order;
import com.rewards.checkout.domain.entity.OrderItem;
import com.rewards.checkout.exception.ApiException;
import com.rewards.checkout.exception.ErrorCode;
import com.rewards.checkout.repository.OrderItemRepository;
import com.rewards.checkout.repository.OrderRepository;
import com.rewards.checkout.web.dto.OrderItemResponse;
import com.rewards.checkout.web.dto.OrderResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    public OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity()))
                .toList();
        return new OrderResponse(order.getId(), order.getCartId(), order.getSubtotal(), order.getDiscount(),
                order.getTotal(), order.getCouponCode(), order.getCreatedAt(), itemResponses);
    }
}
