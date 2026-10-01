package com.mandao.orders;

import com.mandao.orders.ErrandDtos.AmountRequest;
import com.mandao.orders.ErrandDtos.CreateErrandRequest;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.EvidenceView;
import com.mandao.orders.ErrandDtos.OfferView;
import com.mandao.orders.ErrandDtos.RatingRequest;
import com.mandao.orders.ErrandDtos.ReasonRequest;
import com.mandao.evidence.EvidenceService;
import com.mandao.ratings.RatingService;
import com.mandao.ratings.ReputationService;
import com.mandao.ratings.ReputationService.Reputation;
import com.mandao.shared.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** API del cliente (specs 002, 003, 005, 008). */
@RestController
public class ErrandController {

    private final ErrandService errands;
    private final OfferService offers;
    private final EvidenceService evidences;
    private final RatingService ratings;
    private final ReputationService reputation;

    public ErrandController(ErrandService errands, OfferService offers, EvidenceService evidences,
                            RatingService ratings, ReputationService reputation) {
        this.errands = errands;
        this.offers = offers;
        this.evidences = evidences;
        this.ratings = ratings;
        this.reputation = reputation;
    }

    @PostMapping("/api/errands")
    @ResponseStatus(HttpStatus.CREATED)
    public ErrandView create(@Valid @RequestBody CreateErrandRequest request) {
        return errands.create(request, AuthUser.current());
    }

    @GetMapping("/api/errands")
    public List<ErrandSummary> mine() {
        return errands.mine(AuthUser.current());
    }

    @GetMapping("/api/errands/{id}")
    public ErrandView get(@PathVariable("id") UUID id) {
        return errands.get(id, AuthUser.current());
    }

    @PostMapping("/api/errands/{id}/cancel")
    public ErrandView cancel(@PathVariable("id") UUID id, @Valid @RequestBody ReasonRequest request) {
        return errands.cancel(id, request.reason(), AuthUser.current());
    }

    @PostMapping("/api/errands/{id}/confirm")
    public ErrandView confirm(@PathVariable("id") UUID id) {
        return errands.confirm(id, AuthUser.current());
    }

    @PostMapping("/api/errands/{id}/rating")
    public ErrandView rate(@PathVariable("id") UUID id, @Valid @RequestBody RatingRequest request) {
        AuthUser user = AuthUser.current();
        ratings.rate(id, request, user);
        return errands.get(id, user);
    }

    @PostMapping(value = "/api/errands/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceView attach(@PathVariable("id") UUID id, @RequestPart("file") MultipartFile file,
                               @RequestParam(value = "note", required = false) String note) {
        return EvidenceView.of(evidences.addClientAttachment(id, AuthUser.current(), file, note));
    }

    @GetMapping("/api/errands/{id}/offers")
    public List<OfferView> offers(@PathVariable("id") UUID id) {
        return offers.list(id, AuthUser.current());
    }

    @PostMapping("/api/errands/{id}/offers/{offerId}/accept")
    public ErrandView acceptOffer(@PathVariable("id") UUID id, @PathVariable("offerId") UUID offerId) {
        return offers.accept(id, offerId, AuthUser.current());
    }

    @PostMapping("/api/errands/{id}/offers/{offerId}/reject")
    public OfferView rejectOffer(@PathVariable("id") UUID id, @PathVariable("offerId") UUID offerId) {
        return offers.reject(id, offerId, AuthUser.current());
    }

    @PostMapping("/api/errands/{id}/offers/{offerId}/counter")
    public OfferView counterOffer(@PathVariable("id") UUID id, @PathVariable("offerId") UUID offerId,
                                  @Valid @RequestBody AmountRequest request) {
        return offers.counter(id, offerId, request.amount(), AuthUser.current());
    }

    /** AC-008-04: reputación pública (solo agregados, sin datos personales). */
    @GetMapping("/api/couriers/{id}/reputation")
    public Reputation reputation(@PathVariable("id") UUID id) {
        return reputation.of(id);
    }
}
