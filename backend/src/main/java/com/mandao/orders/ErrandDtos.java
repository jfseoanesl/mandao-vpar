package com.mandao.orders;

import com.mandao.couriers.CourierEnums.VehicleType;
import com.mandao.evidence.Evidence;
import com.mandao.payments.Payment;
import com.mandao.payments.PaymentMethod;
import com.mandao.pricing.Complexity;
import com.mandao.ratings.Rating;
import com.mandao.ratings.ReputationService.Reputation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ErrandDtos {

    private ErrandDtos() {
    }

    // ---------- Solicitudes ----------

    public record StopRequest(
            @NotBlank @Size(max = 250) String address,
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lng,
            @Size(max = 500) String instructions,
            @Size(max = 120) String contactName,
            @Pattern(regexp = "^$|^3\\d{9}$", message = "Celular inválido") String contactPhone) {
    }

    public record CreateErrandRequest(
            @NotNull ErrandCategory category,
            @NotBlank @Size(min = 5, max = 2000) String description,
            @NotNull Complexity complexity,
            @NotNull PricingMode pricingMode,
            @NotNull @Size(min = 1, max = 6, message = "Entre 1 y 6 paradas") List<@Valid StopRequest> stops,
            @Min(0) @Max(240) int estimatedWaitMinutes,
            @Min(0) @Max(2_000_000) Long purchaseBudget,
            @NotNull PaymentMethod paymentMethod,
            Instant scheduledFor,
            UUID repeatOf) {
    }

    public record ReasonRequest(@Size(max = 300) String reason) {
    }

    public record OtpRequest(@NotBlank @Pattern(regexp = "^\\d{4}$", message = "Código de 4 dígitos") String otp) {
    }

    public record AmountRequest(@Min(3000) @Max(500_000) long amount, @Size(max = 300) String message) {
    }

    public record AssignRequest(@NotNull UUID courierId) {
    }

    public record PriceRequest(@Min(1000) @Max(2_000_000) long amount) {
    }

    public record RatingRequest(@Min(1) @Max(5) int stars, @Size(max = 5) List<Rating.Tag> tags,
                                @Size(max = 500) String comment) {
    }

    // ---------- Vistas ----------

    public record StopView(int seq, String address, double lat, double lng, String instructions, String contactName,
                           String contactPhone) {
    }

    public record PersonView(UUID id, String fullName, String phone) {
    }

    public record CourierSummary(UUID id, String fullName, String phone, VehicleType vehicleType, String plate,
                                 Reputation reputation) {
    }

    public record HistoryView(ErrandStatus from, ErrandStatus to, String note, Instant at) {
    }

    public record EvidenceView(UUID id, Evidence.Type type, String note, Long amount, Double lat, Double lng,
                               String contentType, boolean hasFile, Instant createdAt) {
        public static EvidenceView of(Evidence e) {
            return new EvidenceView(e.getId(), e.getType(), e.getNote(), e.getAmount(), e.getLat(), e.getLng(),
                    e.getContentType(), e.getObjectKey() != null, e.getCreatedAt());
        }
    }

    public record LocationView(double lat, double lng, Instant at) {
    }

    public record RatingView(int stars, List<String> tags, String comment) {
    }

    public record ErrandView(
            UUID id, String code, ErrandCategory category, String description, Complexity complexity,
            PricingMode pricingMode, ErrandStatus status, List<StopView> stops, BigDecimal distanceKm,
            int estimatedWaitMinutes, long quotedPrice, long totalPrice, long platformFee, long courierPayout,
            long purchaseBudget, long purchasesTotal, long refundTotal, PaymentMethod paymentMethod,
            Payment.Status paymentStatus, Instant scheduledFor, Instant createdAt, Instant assignedAt,
            Instant startedAt, Instant deliveredAt, Instant completedAt, Instant cancelledAt, String cancelReason,
            Boolean needsAttention, String attentionReason, Integer otpFailedAttempts, PersonView client,
            CourierSummary courier, String startOtp, String endOtp, List<HistoryView> history,
            List<EvidenceView> evidences, LocationView courierLocation, RatingView rating, Instant courierPaidAt,
            UUID companyId, ErrandAccess.Viewer viewer) {
    }

    public record ErrandSummary(UUID id, String code, ErrandCategory category, String description,
                                ErrandStatus status, PricingMode pricingMode, long totalPrice, long courierPayout,
                                Instant createdAt, String firstAddress, String lastAddress, String clientName,
                                String courierName, boolean needsAttention, String attentionReason) {
    }

    /** Tarjeta de oportunidad para el mandadero (AC-004-01). */
    public record OpportunityView(UUID id, String code, ErrandCategory category, String description,
                                  Complexity complexity, PricingMode pricingMode, ErrandStatus status,
                                  long courierPayout, long suggestedPrice, BigDecimal routeKm,
                                  Double distanceToOriginKm, int stopsCount, String originAddress,
                                  String destinationAddress, long purchaseBudget, Instant createdAt,
                                  Instant scheduledFor, OfferView myOffer) {
    }

    public record OfferView(UUID id, UUID errandId, CourierSummary courier, long amount, Long counterAmount,
                            String message, ErrandOffer.Status status, Instant createdAt) {
    }
}
