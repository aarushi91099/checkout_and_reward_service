package com.rewards.checkout.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rewards.checkout.config.RewardProperties;
import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.repository.CouponRepository;
import com.rewards.checkout.repository.OrderRepository;
import com.rewards.checkout.service.RewardMilestoneService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RewardMilestoneServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CouponRepository couponRepository;

    private RewardMilestoneService rewardMilestoneService;

    @BeforeEach
    void setUp() {
        RewardProperties properties = new RewardProperties(5, new BigDecimal("10"));
        rewardMilestoneService = new RewardMilestoneService(orderRepository, couponRepository, properties);
    }

    @Test
    void doesNotGenerateWhenOrderCountBelowFirstMilestone() {
        when(orderRepository.count()).thenReturn(4L);

        Optional<Coupon> result = rewardMilestoneService.evaluateAndGenerateIfDue();

        assertThat(result).isEmpty();
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNotGenerateWhenMilestoneAlreadyRewarded() {
        when(orderRepository.count()).thenReturn(5L);
        when(couponRepository.existsByMilestoneNumber(1L)).thenReturn(true);

        Optional<Coupon> result = rewardMilestoneService.evaluateAndGenerateIfDue();

        assertThat(result).isEmpty();
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void generatesCouponForNewlyReachedMilestone() {
        when(orderRepository.count()).thenReturn(10L);
        when(couponRepository.existsByMilestoneNumber(2L)).thenReturn(false);
        when(couponRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Coupon> result = rewardMilestoneService.evaluateAndGenerateIfDue();

        assertThat(result).isPresent();
        assertThat(result.get().getMilestoneNumber()).isEqualTo(2L);
        assertThat(result.get().getDiscountPercent()).isEqualByComparingTo("10");
        verify(couponRepository).saveAndFlush(any());
    }
}
