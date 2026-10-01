package com.mandao.orders;

import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.OfferView;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import com.mandao.notifications.PushMessages;
import com.mandao.notifications.PushService;
import com.mandao.tracking.RealtimePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mandado negociable: ofertas, contraofertas y aceptación (spec 003). */
@Service
public class OfferService {

    private final ErrandOfferRepository offers;
    private final ErrandAccess access;
    private final AssignmentService assignment;
    private final ErrandViews views;
    private final RealtimePublisher realtime;
    private final PushService push;

    public OfferService(ErrandOfferRepository offers, ErrandAccess access, AssignmentService assignment,
                        ErrandViews views, RealtimePublisher realtime, PushService push) {
        this.offers = offers;
        this.access = access;
        this.assignment = assignment;
        this.views = views;
        this.realtime = realtime;
        this.push = push;
    }

    /** AC-003-02. */
    @Transactional
    public OfferView bid(UUID errandId, AuthUser courier, long amount, String message) {
        ErrandAccess.Access a = access.resolve(errandId, courier, true);
        Errand errand = a.errand();
        if (a.viewer() != ErrandAccess.Viewer.PROSPECT || errand.getStatus() != ErrandStatus.NEGOTIATING) {
            throw ApiException.conflict("Este mandado no está recibiendo ofertas");
        }
        boolean hasLive = offers.findFirstByErrandIdAndCourierIdOrderByCreatedAtDesc(errandId, courier.id())
                .map(ErrandOffer::isLive).orElse(false);
        if (hasLive) {
            throw ApiException.conflict("Ya tienes una oferta activa en este mandado");
        }
        ErrandOffer offer = offers.save(new ErrandOffer(errandId, courier.id(), amount,
                ErrandService.blankToNull(message)));
        realtime.errand(errandId, "OFFER_RECEIVED", errand.getStatus().name(),
                Map.of("offerId", offer.getId().toString(), "amount", amount));
        push.notifyUser(errand.getClientId(), PushMessages.offerReceived(errand));
        return views.offer(offer, false);
    }

    /** AC-003-03. */
    @Transactional(readOnly = true)
    public List<OfferView> list(UUID errandId, AuthUser user) {
        ErrandAccess.Access a = access.resolve(errandId, user, false);
        if (a.viewer() == ErrandAccess.Viewer.COURIER) {
            throw ApiException.notFound("Mandado");
        }
        return offers.findByErrandIdOrderByCreatedAtAsc(errandId).stream().map(o -> views.offer(o, true)).toList();
    }

    /** AC-003-04. */
    @Transactional
    public ErrandView accept(UUID errandId, UUID offerId, AuthUser client) {
        Errand errand = access.requireOwner(errandId, client);
        ErrandOffer offer = liveOffer(errandId, offerId);
        if (offer.getStatus() != ErrandOffer.Status.PENDING) {
            throw ApiException.conflict("Ya enviaste una contraoferta; espera la respuesta del mandadero");
        }
        assignment.assign(errand, offer.getCourierId(), offer.getAmount(), client.id(), "Oferta aceptada");
        closeOffers(errandId, offer);
        return views.view(errand, ErrandAccess.Viewer.OWNER);
    }

    @Transactional
    public OfferView reject(UUID errandId, UUID offerId, AuthUser client) {
        access.requireOwner(errandId, client);
        ErrandOffer offer = liveOffer(errandId, offerId);
        offer.setStatus(ErrandOffer.Status.REJECTED);
        realtime.courier(offer.getCourierId(), "OFFER_REJECTED", errandId, Map.of());
        return views.offer(offer, true);
    }

    /** AC-003-05 (cliente contraoferta). */
    @Transactional
    public OfferView counter(UUID errandId, UUID offerId, long amount, AuthUser client) {
        Errand errand = access.requireOwner(errandId, client);
        if (errand.getStatus() != ErrandStatus.NEGOTIATING) {
            throw ApiException.conflict("Este mandado ya no está en negociación");
        }
        ErrandOffer offer = liveOffer(errandId, offerId);
        offer.counter(amount);
        realtime.courier(offer.getCourierId(), "OFFER_COUNTERED", errandId, Map.of("amount", amount));
        push.notifyUser(offer.getCourierId(), PushMessages.countered(errand, amount));
        return views.offer(offer, true);
    }

    /** AC-003-05 (mandadero acepta la contraoferta). */
    @Transactional
    public ErrandView acceptCounter(UUID errandId, AuthUser courier) {
        ErrandAccess.Access a = access.resolve(errandId, courier, true);
        if (a.viewer() != ErrandAccess.Viewer.PROSPECT) {
            throw ApiException.notFound("Mandado");
        }
        ErrandOffer offer = offers.findFirstByErrandIdAndCourierIdOrderByCreatedAtDesc(errandId, courier.id())
                .filter(o -> o.getStatus() == ErrandOffer.Status.COUNTERED)
                .orElseThrow(() -> ApiException.conflict("No tienes una contraoferta pendiente"));
        assignment.assign(a.errand(), courier.id(), offer.getCounterAmount(), courier.id(), "Contraoferta aceptada");
        closeOffers(errandId, offer);
        return views.view(a.errand(), ErrandAccess.Viewer.COURIER);
    }

    private ErrandOffer liveOffer(UUID errandId, UUID offerId) {
        ErrandOffer offer = offers.findById(offerId).filter(o -> o.getErrandId().equals(errandId))
                .orElseThrow(() -> ApiException.notFound("Oferta"));
        if (!offer.isLive()) {
            throw ApiException.conflict("Esta oferta ya no está vigente");
        }
        return offer;
    }

    private void closeOffers(UUID errandId, ErrandOffer winner) {
        winner.setStatus(ErrandOffer.Status.ACCEPTED);
        for (ErrandOffer o : offers.findByErrandIdOrderByCreatedAtAsc(errandId)) {
            if (!o.getId().equals(winner.getId()) && o.isLive()) {
                o.setStatus(ErrandOffer.Status.REJECTED);
            }
        }
    }
}
