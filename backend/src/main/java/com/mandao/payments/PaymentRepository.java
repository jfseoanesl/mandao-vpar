package com.mandao.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByErrandId(UUID errandId);

    List<Payment> findTop300ByStatusOrderByCreatedAtDesc(Payment.Status status);

    List<Payment> findTop300ByOrderByCreatedAtDesc();
}
