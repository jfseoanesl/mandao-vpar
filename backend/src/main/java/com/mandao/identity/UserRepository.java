package com.mandao.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    List<User> findTop200ByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.role = :role and u.active = true")
    List<UUID> findActiveIdsByRole(Role role);
}
