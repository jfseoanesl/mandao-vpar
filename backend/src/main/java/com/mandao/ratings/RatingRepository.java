package com.mandao.ratings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByErrandId(UUID errandId);

    Optional<Rating> findByErrandId(UUID errandId);

    List<Rating> findByCourierId(UUID courierId);
}
