package com.mandao.orders;

import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.evidence.EvidenceRepository;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.orders.ErrandAccess.Viewer;
import com.mandao.orders.ErrandDtos.CourierSummary;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.EvidenceView;
import com.mandao.orders.ErrandDtos.HistoryView;
import com.mandao.orders.ErrandDtos.LocationView;
import com.mandao.orders.ErrandDtos.OfferView;
import com.mandao.orders.ErrandDtos.PersonView;
import com.mandao.orders.ErrandDtos.RatingView;
import com.mandao.orders.ErrandDtos.StopView;
import com.mandao.payments.SettlementService;
import com.mandao.ratings.RatingRepository;
import com.mandao.ratings.ReputationService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Construye la vista del mandado según quién la mira (principio de mínimo privilegio). */
@Component
public class ErrandViews {

    private final UserRepository users;
    private final CourierProfileRepository couriers;
    private final ErrandStatusHistoryRepository history;
    private final EvidenceRepository evidences;
    private final RatingRepository ratings;
    private final ReputationService reputation;
    private final SettlementService settlement;
    private final OtpCipher otp;

    public ErrandViews(UserRepository users, CourierProfileRepository couriers, ErrandStatusHistoryRepository history,
                       EvidenceRepository evidences, RatingRepository ratings, ReputationService reputation,
                       SettlementService settlement, OtpCipher otp) {
        this.users = users;
        this.couriers = couriers;
        this.history = history;
        this.evidences = evidences;
        this.ratings = ratings;
        this.reputation = reputation;
        this.settlement = settlement;
        this.otp = otp;
    }

    public ErrandView view(Errand e, Viewer viewer) {
        boolean owner = viewer == Viewer.OWNER;
        boolean admin = viewer == Viewer.ADMIN;
        boolean prospect = viewer == Viewer.PROSPECT;
        boolean showOtp = owner && !ErrandStatus.FINAL.contains(e.getStatus());

        List<StopView> stops = e.getStops().stream()
                .map(s -> new StopView(s.getSeq(), s.getAddress(), s.getLat(), s.getLng(), s.getInstructions(),
                        prospect ? null : s.getContactName(), prospect ? null : s.getContactPhone()))
                .toList();

        User client = users.findById(e.getClientId()).orElse(null);
        PersonView clientView = client == null ? null : new PersonView(client.getId(),
                prospect ? firstName(client.getFullName()) : client.getFullName(),
                prospect ? null : client.getPhone());

        CourierSummary courier = e.getCourierId() == null ? null : courierSummary(e.getCourierId());
        LocationView location = null;
        if (e.getCourierId() != null && ErrandStatus.COURIER_ACTIVE.contains(e.getStatus()) && !prospect) {
            CourierProfile p = couriers.findById(e.getCourierId()).orElse(null);
            if (p != null && p.getLat() != null) {
                location = new LocationView(p.getLat(), p.getLng(), p.getLastPingAt());
            }
        }

        List<HistoryView> hist = prospect ? List.of() : history.findByErrandIdOrderByCreatedAtAsc(e.getId()).stream()
                .map(h -> new HistoryView(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getCreatedAt())).toList();
        List<EvidenceView> evs = prospect ? List.of() : evidences.findByErrandIdOrderByCreatedAtAsc(e.getId())
                .stream().map(EvidenceView::of).toList();
        RatingView rating = prospect ? null : ratings.findByErrandId(e.getId())
                .map(r -> new RatingView(r.getStars(),
                        r.getTags() == null ? List.of() : Arrays.asList(r.getTags().split(",")), r.getComment()))
                .orElse(null);

        return new ErrandView(e.getId(), e.getCode(), e.getCategory(), e.getDescription(), e.getComplexity(),
                e.getPricingMode(), e.getStatus(), stops, e.getDistanceKm(), e.getEstimatedWaitMin(),
                e.getQuotedPrice(), e.getTotalPrice(), e.getPlatformFee(), e.getCourierPayout(),
                e.getPurchaseBudget(), settlement.purchasesTotal(e.getId()), settlement.refundTotal(e.getId()),
                e.getPaymentMethod(), prospect ? null : settlement.paymentStatus(e.getId()), e.getScheduledFor(),
                e.getCreatedAt(), e.getAssignedAt(), e.getStartedAt(), e.getDeliveredAt(), e.getCompletedAt(),
                e.getCancelledAt(), e.getCancelReason(),
                admin ? e.isNeedsAttention() : null, admin ? e.getAttentionReason() : null,
                admin ? e.getOtpFailedAttempts() : null,
                clientView, courier,
                showOtp ? otp.decrypt(e.getStartOtpEnc()) : null,
                showOtp ? otp.decrypt(e.getEndOtpEnc()) : null,
                hist, evs, location, rating, admin || viewer == Viewer.COURIER ? e.getCourierPaidAt() : null,
                e.getCompanyId(), viewer);
    }

    public ErrandSummary summary(Errand e) {
        List<ErrandStop> stops = e.getStops();
        String clientName = users.findById(e.getClientId()).map(User::getFullName).orElse(null);
        String courierName = e.getCourierId() == null ? null
                : users.findById(e.getCourierId()).map(User::getFullName).orElse(null);
        String desc = e.getDescription().length() > 140 ? e.getDescription().substring(0, 140) + "…"
                : e.getDescription();
        return new ErrandSummary(e.getId(), e.getCode(), e.getCategory(), desc, e.getStatus(), e.getPricingMode(),
                e.getTotalPrice(), e.getCourierPayout(), e.getCreatedAt(),
                stops.isEmpty() ? null : stops.get(0).getAddress(),
                stops.isEmpty() ? null : stops.get(stops.size() - 1).getAddress(),
                clientName, courierName, e.isNeedsAttention(), e.getAttentionReason());
    }

    public CourierSummary courierSummary(UUID courierId) {
        User u = users.findById(courierId).orElse(null);
        CourierProfile p = couriers.findById(courierId).orElse(null);
        return new CourierSummary(courierId, u == null ? null : u.getFullName(), u == null ? null : u.getPhone(),
                p == null ? null : p.getVehicleType(), p == null ? null : p.getPlate(), reputation.of(courierId));
    }

    /** En ofertas no se expone el celular del mandadero: solo se comparte al quedar asignado. */
    public OfferView offer(ErrandOffer o, boolean includeCourier) {
        CourierSummary c = null;
        if (includeCourier) {
            CourierSummary full = courierSummary(o.getCourierId());
            c = new CourierSummary(full.id(), full.fullName(), null, full.vehicleType(), full.plate(),
                    full.reputation());
        }
        return new OfferView(o.getId(), o.getErrandId(), c, o.getAmount(), o.getCounterAmount(), o.getMessage(),
                o.getStatus(), o.getCreatedAt());
    }

    private static String firstName(String full) {
        return full == null ? null : full.trim().split("\\s+")[0];
    }
}
