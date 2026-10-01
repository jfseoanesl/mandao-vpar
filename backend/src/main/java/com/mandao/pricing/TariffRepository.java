package com.mandao.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TariffRepository extends JpaRepository<Tariff, UUID> {

    Optional<Tariff> findFirstByActiveTrueOrderByVersionDesc();

    Optional<Tariff> findFirstByOrderByVersionDesc();

    List<Tariff> findAllByOrderByVersionDesc();
}
