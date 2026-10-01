package com.mandao.orders;

import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.payments.SettlementService;
import com.mandao.pricing.PricingEngine;
import com.mandao.pricing.PricingService;
import com.mandao.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reglas comunes para asignar un mandadero a un mandado (spec 004). */
@Service
public class AssignmentService {

    private final ErrandRepository errands;
    private final CourierProfileRepository couriers;
    private final ErrandLifecycle lifecycle;
    private final PricingService pricing;
    private final SettlementService settlement;

    public AssignmentService(ErrandRepository errands, CourierProfileRepository couriers, ErrandLifecycle lifecycle,
                             PricingService pricing, SettlementService settlement) {
        this.errands = errands;
        this.couriers = couriers;
        this.lifecycle = lifecycle;
        this.pricing = pricing;
        this.settlement = settlement;
    }

    /** Verifica AC-004-04 / AC-004-05. */
    public void ensureCourierCanTake(UUID courierId) {
        CourierProfile profile = couriers.findById(courierId).orElseThrow(() -> ApiException.notFound("Mandadero"));
        if (!profile.isApproved()) {
            throw ApiException.forbidden("Tu cuenta aún no ha sido aprobada");
        }
        if (errands.existsByCourierIdAndStatusIn(courierId, ErrandStatus.COURIER_ACTIVE)) {
            throw ApiException.conflict("Ya tienes un mandado activo. Termínalo antes de tomar otro.");
        }
    }

    /**
     * Asigna el mandadero. {@code negotiatedPrice} fija el precio (spec 003); si es null se conserva la cotización.
     * El {@code @Version} de Errand garantiza que solo un mandadero gane (AC-004-03).
     */
    @Transactional
    public Errand assign(Errand errand, UUID courierId, Long negotiatedPrice, UUID actorId, String note) {
        if (!ErrandStatus.OPEN.contains(errand.getStatus())) {
            throw ApiException.conflict("Este mandado ya fue tomado");
        }
        ensureCourierCanTake(courierId);
        if (negotiatedPrice != null) {
            PricingEngine.Split split = PricingEngine.split(negotiatedPrice, pricing.activeTariff().getCommissionPct());
            errand.setPrice(negotiatedPrice, split.platformFee(), split.courierPayout());
            settlement.onPriceChanged(errand);
        }
        errand.assignCourier(courierId);
        errand.resolveAttention();
        return lifecycle.transition(errand, ErrandStatus.ASSIGNED, actorId, note);
    }
}
