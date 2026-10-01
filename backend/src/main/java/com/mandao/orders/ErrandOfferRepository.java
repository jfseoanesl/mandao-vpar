package com.mandao.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErrandOfferRepository extends JpaRepository<ErrandOffer, UUID> {

    List<ErrandOffer> findByErrandIdOrderByCreatedAtAsc(UUID errandId);

    List<ErrandOffer> findByCourierIdAndErrandIdIn(UUID courierId, List<UUID> errandIds);

    Optional<ErrandOffer> findFirstByErrandIdAndCourierIdOrderByCreatedAtDesc(UUID errandId, UUID courierId);
}
