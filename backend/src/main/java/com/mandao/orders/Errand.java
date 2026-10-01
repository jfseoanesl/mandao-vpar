package com.mandao.orders;

import com.mandao.payments.PaymentMethod;
import com.mandao.pricing.Complexity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "errands")
public class Errand {

    @Id
    private UUID id;
    private String code;
    private UUID clientId;
    private UUID companyId;
    private UUID courierId;
    @Enumerated(EnumType.STRING)
    private ErrandCategory category;
    private String description;
    @Enumerated(EnumType.STRING)
    private Complexity complexity;
    @Enumerated(EnumType.STRING)
    private PricingMode pricingMode;
    @Enumerated(EnumType.STRING)
    private ErrandStatus status;
    private int estimatedWaitMin;
    private BigDecimal distanceKm;
    private long quotedPrice;
    private long totalPrice;
    private long platformFee;
    private long courierPayout;
    private int tariffVersion;
    private long purchaseBudget;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    private Instant scheduledFor;
    private UUID repeatOf;
    private String startOtpEnc;
    private String endOtpEnc;
    private int otpFailedAttempts;
    private boolean needsAttention;
    private String attentionReason;
    private String cancelReason;
    private Instant createdAt;
    private Instant assignedAt;
    private Instant startedAt;
    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private Instant courierPaidAt;
    @Version
    private Long version; // null = nuevo (Spring Data usa persist en vez de merge)

    @OneToMany(mappedBy = "errand", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<ErrandStop> stops = new ArrayList<>();

    protected Errand() {
    }

    public Errand(String code, UUID clientId, UUID companyId, ErrandCategory category, String description,
                  Complexity complexity, PricingMode pricingMode, int estimatedWaitMin, BigDecimal distanceKm,
                  long quotedPrice, long platformFee, long courierPayout, int tariffVersion, long purchaseBudget,
                  PaymentMethod paymentMethod, Instant scheduledFor, UUID repeatOf, String startOtpEnc,
                  String endOtpEnc) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.clientId = clientId;
        this.companyId = companyId;
        this.category = category;
        this.description = description;
        this.complexity = complexity;
        this.pricingMode = pricingMode;
        this.status = pricingMode == PricingMode.NEGOTIABLE ? ErrandStatus.NEGOTIATING : ErrandStatus.SEARCHING;
        this.estimatedWaitMin = estimatedWaitMin;
        this.distanceKm = distanceKm;
        this.quotedPrice = quotedPrice;
        this.totalPrice = quotedPrice;
        this.platformFee = platformFee;
        this.courierPayout = courierPayout;
        this.tariffVersion = tariffVersion;
        this.purchaseBudget = purchaseBudget;
        this.paymentMethod = paymentMethod;
        this.scheduledFor = scheduledFor;
        this.repeatOf = repeatOf;
        this.startOtpEnc = startOtpEnc;
        this.endOtpEnc = endOtpEnc;
        this.createdAt = Instant.now();
    }

    public void addStop(String address, double lat, double lng, String instructions, String contactName,
                        String contactPhone) {
        stops.add(new ErrandStop(this, stops.size(), address, lat, lng, instructions, contactName, contactPhone));
    }

    /** Cambia el estado y sella el momento correspondiente. Solo lo invoca {@link ErrandLifecycle}. */
    void applyStatus(ErrandStatus to) {
        Instant now = Instant.now();
        switch (to) {
            case ASSIGNED -> assignedAt = now;
            case IN_PROGRESS -> startedAt = now;
            case DELIVERED -> deliveredAt = now;
            case COMPLETED -> completedAt = now;
            case CANCELLED -> cancelledAt = now;
            default -> {
            }
        }
        this.status = to;
    }

    public void assignCourier(UUID courierId) {
        this.courierId = courierId;
    }

    public void setPrice(long total, long platformFee, long courierPayout) {
        this.totalPrice = total;
        this.platformFee = platformFee;
        this.courierPayout = courierPayout;
    }

    public int registerFailedOtp() {
        return ++otpFailedAttempts;
    }

    public void flagAttention(String reason) {
        this.needsAttention = true;
        this.attentionReason = reason;
    }

    public void resolveAttention() {
        this.needsAttention = false;
        this.attentionReason = null;
        this.otpFailedAttempts = 0;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public void markCourierPaid() {
        this.courierPaidAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public ErrandCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Complexity getComplexity() {
        return complexity;
    }

    public PricingMode getPricingMode() {
        return pricingMode;
    }

    public ErrandStatus getStatus() {
        return status;
    }

    public int getEstimatedWaitMin() {
        return estimatedWaitMin;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public long getQuotedPrice() {
        return quotedPrice;
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public long getPlatformFee() {
        return platformFee;
    }

    public long getCourierPayout() {
        return courierPayout;
    }

    public int getTariffVersion() {
        return tariffVersion;
    }

    public long getPurchaseBudget() {
        return purchaseBudget;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public UUID getRepeatOf() {
        return repeatOf;
    }

    public String getStartOtpEnc() {
        return startOtpEnc;
    }

    public String getEndOtpEnc() {
        return endOtpEnc;
    }

    public int getOtpFailedAttempts() {
        return otpFailedAttempts;
    }

    public boolean isNeedsAttention() {
        return needsAttention;
    }

    public String getAttentionReason() {
        return attentionReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCourierPaidAt() {
        return courierPaidAt;
    }

    public List<ErrandStop> getStops() {
        return stops;
    }

    public ErrandStop firstStop() {
        return stops.get(0);
    }
}
