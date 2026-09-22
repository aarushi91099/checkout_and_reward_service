package com.rewards.checkout.repository;

import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.domain.enums.CouponStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CouponRepository extends JpaRepository<Coupon, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.code = :code")
    Optional<Coupon> findByCodeForUpdate(String code);

    boolean existsByMilestoneNumber(long milestoneNumber);

    long countByStatus(CouponStatus status);
}
