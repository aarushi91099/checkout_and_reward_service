package com.rewards.checkout.repository;

import com.rewards.checkout.domain.entity.Cart;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id = :id")
    Optional<Cart> findByIdForUpdate(UUID id);
}
