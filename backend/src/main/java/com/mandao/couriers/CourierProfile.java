package com.mandao.couriers;

import com.mandao.couriers.CourierEnums.CourierStatus;
import com.mandao.couriers.CourierEnums.PayoutMethod;
import com.mandao.couriers.CourierEnums.VehicleType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_profiles")
public class CourierProfile {

    @Id
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;
    private String plate;
    @Enumerated(EnumType.STRING)
    private PayoutMethod payoutMethod;
    private String payoutAccount;
    @Enumerated(EnumType.STRING)
    private CourierStatus status;
    private String rejectionReason;
    private boolean available;
    private Double lat;
    private Double lng;
    private Instant lastPingAt;
    private Instant approvedAt;
    @Version
    private Long version; // null = nuevo (Spring Data usa persist en vez de merge)

    protected CourierProfile() {
    }

    public CourierProfile(UUID userId) {
        this.userId = userId;
        this.status = CourierStatus.PENDING;
    }

    public void updateDetails(VehicleType vehicleType, String plate, PayoutMethod payoutMethod, String payoutAccount) {
        this.vehicleType = vehicleType;
        this.plate = plate;
        this.payoutMethod = payoutMethod;
        this.payoutAccount = payoutAccount;
    }

    public void approve() {
        this.status = CourierStatus.APPROVED;
        this.rejectionReason = null;
        this.approvedAt = Instant.now();
    }

    public void reject(String reason) {
        this.status = CourierStatus.REJECTED;
        this.rejectionReason = reason;
        this.available = false;
    }

    /** Tras corregir documentos, un mandadero rechazado vuelve a revisión. */
    public void resubmit() {
        this.status = CourierStatus.PENDING;
    }

    public void updateLocation(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
        this.lastPingAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getPlate() {
        return plate;
    }

    public PayoutMethod getPayoutMethod() {
        return payoutMethod;
    }

    public String getPayoutAccount() {
        return payoutAccount;
    }

    public CourierStatus getStatus() {
        return status;
    }

    public boolean isApproved() {
        return status == CourierStatus.APPROVED;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public Instant getLastPingAt() {
        return lastPingAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
