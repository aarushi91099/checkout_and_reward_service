package com.rewards.checkout.service;

import com.rewards.checkout.domain.enums.CouponStatus;
import com.rewards.checkout.repository.CouponRepository;
import com.rewards.checkout.repository.OrderItemRepository;
import com.rewards.checkout.repository.OrderRepository;
import com.rewards.checkout.repository.OrderTotals;
import com.rewards.checkout.support.Money;
import com.rewards.checkout.web.dto.CouponSummaryResponse;
import com.rewards.checkout.web.dto.ProductSummaryResponse;
import com.rewards.checkout.web.dto.ReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only: every method here is a report query, never mutates state.
 */
@Service
public class ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CouponRepository couponRepository;

    public ReportService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                          CouponRepository couponRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport() {
        OrderTotals totals = orderRepository.sumTotals();
        var products = orderItemRepository.aggregateByProduct().stream()
                .map(p -> new ProductSummaryResponse(p.getProductId(), p.getProductName(), p.getQuantitySold(),
                        Money.normalize(p.getRevenue())))
                .toList();
        var couponSummary = new CouponSummaryResponse(
                couponRepository.count(),
                couponRepository.countByStatus(CouponStatus.REDEEMED));

        return new ReportResponse(
                orderRepository.count(),
                Money.normalize(totals.getGrossRevenue()),
                Money.normalize(totals.getDiscounts()),
                Money.normalize(totals.getNetRevenue()),
                products,
                couponSummary);
    }
}
