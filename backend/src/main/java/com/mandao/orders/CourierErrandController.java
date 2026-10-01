package com.mandao.orders;

import com.mandao.evidence.Evidence;
import com.mandao.evidence.EvidenceService;
import com.mandao.orders.ErrandDtos.AmountRequest;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.EvidenceView;
import com.mandao.orders.ErrandDtos.OfferView;
import com.mandao.orders.ErrandDtos.OpportunityView;
import com.mandao.orders.ErrandDtos.OtpRequest;
import com.mandao.orders.ErrandDtos.ReasonRequest;
import com.mandao.shared.ApiException;
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

/** API del mandadero (specs 003, 004, 005). */
@RestController
public class CourierErrandController {

    private final CourierErrandService service;
    private final OfferService offers;
    private final EvidenceService evidences;

    public CourierErrandController(CourierErrandService service, OfferService offers, EvidenceService evidences) {
        this.service = service;
        this.offers = offers;
        this.evidences = evidences;
    }

    @GetMapping("/api/courier/opportunities")
    public List<OpportunityView> opportunities() {
        return service.opportunities(AuthUser.current());
    }

    @GetMapping("/api/courier/errands")
    public List<ErrandSummary> mine() {
        return service.mine(AuthUser.current());
    }

    @PostMapping("/api/courier/errands/{id}/accept")
    public ErrandView accept(@PathVariable("id") UUID id) {
        return service.accept(id, AuthUser.current());
    }

    @PostMapping("/api/courier/errands/{id}/bid")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferView bid(@PathVariable("id") UUID id, @Valid @RequestBody AmountRequest request) {
        return offers.bid(id, AuthUser.current(), request.amount(), request.message());
    }

    @PostMapping("/api/courier/errands/{id}/accept-counter")
    public ErrandView acceptCounter(@PathVariable("id") UUID id) {
        return offers.acceptCounter(id, AuthUser.current());
    }

    @PostMapping("/api/courier/errands/{id}/on-the-way")
    public ErrandView onTheWay(@PathVariable("id") UUID id) {
        return service.onTheWay(id, AuthUser.current());
    }

    @PostMapping("/api/courier/errands/{id}/start")
    public ErrandView start(@PathVariable("id") UUID id, @Valid @RequestBody OtpRequest request) {
        return service.start(id, request.otp(), AuthUser.current());
    }

    @PostMapping("/api/courier/errands/{id}/deliver")
    public ErrandView deliver(@PathVariable("id") UUID id, @Valid @RequestBody OtpRequest request) {
        return service.deliver(id, request.otp(), AuthUser.current());
    }

    @PostMapping("/api/courier/errands/{id}/fail")
    public ErrandView fail(@PathVariable("id") UUID id, @Valid @RequestBody ReasonRequest request) {
        return service.fail(id, request.reason(), AuthUser.current());
    }

    @PostMapping(value = "/api/courier/errands/{id}/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceView addEvidence(@PathVariable("id") UUID id,
                                    @RequestParam("type") Evidence.Type type,
                                    @RequestPart(value = "file", required = false) MultipartFile file,
                                    @RequestParam(value = "note", required = false) String note,
                                    @RequestParam(value = "amount", required = false) Long amount,
                                    @RequestParam(value = "lat", required = false) Double lat,
                                    @RequestParam(value = "lng", required = false) Double lng) {
        if (amount != null && (amount < 0 || amount > 2_000_000)) {
            throw ApiException.badRequest("Valor de factura inválido");
        }
        if ((lat != null && Math.abs(lat) > 90) || (lng != null && Math.abs(lng) > 180)) {
            throw ApiException.badRequest("Coordenadas inválidas");
        }
        var input = new EvidenceService.CourierEvidence(type, note, amount, lat, lng);
        return EvidenceView.of(evidences.addCourierEvidence(id, AuthUser.current(), input, file));
    }
}
