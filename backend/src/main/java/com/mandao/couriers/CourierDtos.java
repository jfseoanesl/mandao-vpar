package com.mandao.couriers;

import com.mandao.couriers.CourierEnums.CourierStatus;
import com.mandao.couriers.CourierEnums.DocumentType;
import com.mandao.couriers.CourierEnums.PayoutMethod;
import com.mandao.couriers.CourierEnums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CourierDtos {

    private CourierDtos() {
    }

    public record UpdateCourierRequest(
            @NotNull VehicleType vehicleType,
            @Pattern(regexp = "^[A-Za-z0-9]{0,8}$", message = "Placa inválida") String plate,
            @NotNull PayoutMethod payoutMethod,
            @NotBlank @Pattern(regexp = "^[0-9-]{6,30}$", message = "Número de cuenta inválido") String payoutAccount) {
    }

    public record AvailabilityRequest(boolean available) {
    }

    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record DocumentView(UUID id, DocumentType type, Instant uploadedAt) {
    }

    public record CourierView(UUID userId, String fullName, String phone, CourierStatus status, String rejectionReason,
                              VehicleType vehicleType, String plate, PayoutMethod payoutMethod, String payoutAccount,
                              boolean available, Double lat, Double lng, Instant lastPingAt, Instant approvedAt,
                              List<DocumentView> documents, List<DocumentType> missingDocuments) {
    }
}
