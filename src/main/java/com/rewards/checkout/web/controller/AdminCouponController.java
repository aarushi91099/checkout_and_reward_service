package com.rewards.checkout.web.controller;

import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.service.RewardMilestoneService;
import com.rewards.checkout.web.dto.CouponGenerateResponse;
import com.rewards.checkout.web.dto.CouponResponse;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/coupons")
public class AdminCouponController {

    private final RewardMilestoneService rewardMilestoneService;

    public AdminCouponController(RewardMilestoneService rewardMilestoneService) {
        this.rewardMilestoneService = rewardMilestoneService;
    }

    @PostMapping("/generate")
    public ResponseEntity<CouponGenerateResponse> generate() {
        Optional<Coupon> generated;
        try {
            generated = rewardMilestoneService.evaluateAndGenerateIfDue();
        } catch (DataIntegrityViolationException ex) {
            // a concurrent checkout's milestone check already generated this milestone's coupon
            generated = Optional.empty();
        }
        CouponResponse couponResponse = generated
                .map(c -> new CouponResponse(c.getCode(), c.getDiscountPercent(), c.getStatus(), c.getMilestoneNumber()))
                .orElse(null);
        return ResponseEntity.ok(new CouponGenerateResponse(generated.isPresent(), couponResponse));
    }
}
