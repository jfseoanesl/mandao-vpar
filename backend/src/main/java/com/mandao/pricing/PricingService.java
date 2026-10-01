package com.mandao.pricing;

import com.mandao.pricing.PricingDtos.TariffRequest;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuditService;
import com.mandao.shared.Geo;
import com.mandao.shared.MandaoProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PricingService {

    private final TariffRepository tariffs;
    private final DistanceService distances;
    private final MandaoProperties.ServiceArea area;
    private final AuditService audit;

    public PricingService(TariffRepository tariffs, DistanceService distances, MandaoProperties props,
                          AuditService audit) {
        this.tariffs = tariffs;
        this.distances = distances;
        this.area = props.serviceArea();
        this.audit = audit;
    }

    public PricingEngine.Breakdown quote(List<GeoPoint> stops, int waitMinutes, Complexity complexity) {
        validateServiceArea(stops);
        double km = distances.routeKm(stops);
        return PricingEngine.quote(activeTariff(), km, stops.size(), waitMinutes, complexity);
    }

    public Tariff activeTariff() {
        return tariffs.findFirstByActiveTrueOrderByVersionDesc()
                .orElseThrow(() -> new IllegalStateException("No hay tarifa activa"));
    }

    /** AC-002-04: todas las paradas dentro del área de servicio. */
    public void validateServiceArea(List<GeoPoint> stops) {
        for (GeoPoint p : stops) {
            if (Geo.haversineKm(area.centerLat(), area.centerLng(), p.lat(), p.lng()) > area.radiusKm()) {
                throw ApiException.badRequest("Por ahora solo operamos en Valledupar y alrededores");
            }
        }
    }

    @Transactional
    public Tariff newVersion(TariffRequest req, UUID adminId) {
        Tariff current = activeTariff();
        int next = tariffs.findFirstByOrderByVersionDesc().map(Tariff::getVersion).orElse(0) + 1;
        current.deactivate();
        tariffs.saveAndFlush(current);
        Tariff created = tariffs.save(new Tariff(next, req.baseFare(), req.perKm(), req.includedWaitMin(),
                req.perExtraMin(), req.perExtraStop(), req.complexityMedium(), req.complexityComplex(),
                req.commissionPct(), adminId));
        audit.record(adminId, "TARIFF_CHANGED", "tariff", next, req.toString());
        return created;
    }
}
