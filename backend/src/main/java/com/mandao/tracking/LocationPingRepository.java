package com.mandao.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {

    List<LocationPing> findTop300ByErrandIdOrderByCreatedAtDesc(UUID errandId);

    long countByErrandId(UUID errandId);

    @Modifying
    @Transactional
    @Query("delete from LocationPing p where p.createdAt < :before")
    int deleteOlderThan(Instant before);
}
