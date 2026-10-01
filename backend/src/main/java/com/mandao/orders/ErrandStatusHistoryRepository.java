package com.mandao.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ErrandStatusHistoryRepository extends JpaRepository<ErrandStatusHistory, UUID> {

    List<ErrandStatusHistory> findByErrandIdOrderByCreatedAtAsc(UUID errandId);
}
