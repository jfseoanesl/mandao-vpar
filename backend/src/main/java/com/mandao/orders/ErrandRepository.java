package com.mandao.orders;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErrandRepository extends JpaRepository<Errand, UUID> {

    boolean existsByCode(String code);

    List<Errand> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable page);

    List<Errand> findByCourierIdOrderByCreatedAtDesc(UUID courierId, Pageable page);

    List<Errand> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable page);

    List<Errand> findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID companyId, Instant from, Instant to);

    List<Errand> findByStatusIn(Collection<ErrandStatus> statuses);

    List<Errand> findByStatusAndDeliveredAtBefore(ErrandStatus status, Instant before);

    boolean existsByCourierIdAndStatusIn(UUID courierId, Collection<ErrandStatus> statuses);

    Optional<Errand> findFirstByCourierIdAndStatusIn(UUID courierId, Collection<ErrandStatus> statuses);

    List<Errand> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);

    @Query("select e from Errand e where (:status is null or e.status = :status) "
            + "order by e.needsAttention desc, e.createdAt desc")
    List<Errand> findForAdmin(ErrandStatus status, Pageable page);
}
