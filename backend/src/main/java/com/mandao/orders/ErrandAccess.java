package com.mandao.orders;

import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.identity.Role;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Autorización por pertenencia (P4, AC-001-08). Si el usuario no tiene relación con el mandado
 * responde 404 para no revelar que existe.
 */
@Component
public class ErrandAccess {

    public enum Viewer { OWNER, COMPANY_ADMIN, COURIER, PROSPECT, ADMIN }

    public record Access(Errand errand, Viewer viewer) {
        public boolean isOwner() {
            return viewer == Viewer.OWNER;
        }
    }

    private final ErrandRepository errands;
    private final CourierProfileRepository couriers;

    public ErrandAccess(ErrandRepository errands, CourierProfileRepository couriers) {
        this.errands = errands;
        this.couriers = couriers;
    }

    /**
     * @param allowProspect permite a mandaderos aprobados ver mandados abiertos (para decidir si los toman),
     *                      con datos reducidos. Nunca se usa para archivos ni acciones.
     */
    public Access resolve(UUID errandId, AuthUser user, boolean allowProspect) {
        Errand errand = errands.findById(errandId).orElseThrow(() -> ApiException.notFound("Mandado"));
        Viewer viewer = viewerOf(errand, user, allowProspect);
        if (viewer == null) {
            throw ApiException.notFound("Mandado");
        }
        return new Access(errand, viewer);
    }

    public Errand requireOwner(UUID errandId, AuthUser user) {
        Access access = resolve(errandId, user, false);
        if (!access.isOwner()) {
            throw ApiException.notFound("Mandado");
        }
        return access.errand();
    }

    public Errand requireAssignedCourier(UUID errandId, AuthUser user) {
        Access access = resolve(errandId, user, false);
        if (access.viewer() != Viewer.COURIER) {
            throw ApiException.notFound("Mandado");
        }
        return access.errand();
    }

    private Viewer viewerOf(Errand errand, AuthUser user, boolean allowProspect) {
        if (user.isAdmin()) {
            return Viewer.ADMIN;
        }
        if (errand.getClientId().equals(user.id())) {
            return Viewer.OWNER;
        }
        if (user.role() == Role.COMPANY_ADMIN && errand.getCompanyId() != null
                && errand.getCompanyId().equals(user.companyId())) {
            return Viewer.COMPANY_ADMIN;
        }
        if (user.role() == Role.COURIER) {
            if (user.id().equals(errand.getCourierId())) {
                return Viewer.COURIER;
            }
            if (allowProspect && ErrandStatus.OPEN.contains(errand.getStatus())
                    && couriers.findById(user.id()).map(CourierProfile::isApproved).orElse(false)) {
                return Viewer.PROSPECT;
            }
        }
        return null;
    }
}
