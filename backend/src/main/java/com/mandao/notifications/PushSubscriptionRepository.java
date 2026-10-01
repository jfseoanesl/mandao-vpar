package com.mandao.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdIn(Collection<UUID> userIds);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.userId = :userId")
    int deleteByEndpointAndUserId(String endpoint, UUID userId);

    @Modifying
    @Transactional
    @Query("update PushSubscription s set s.lastUsedAt = :at where s.endpoint = :endpoint")
    int markUsed(String endpoint, java.time.Instant at);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(String endpoint);
}
