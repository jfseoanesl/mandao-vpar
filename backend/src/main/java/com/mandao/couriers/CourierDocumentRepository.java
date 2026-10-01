package com.mandao.couriers;

import com.mandao.couriers.CourierEnums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierDocumentRepository extends JpaRepository<CourierDocument, UUID> {

    List<CourierDocument> findByCourierId(UUID courierId);

    Optional<CourierDocument> findByCourierIdAndType(UUID courierId, DocumentType type);
}
