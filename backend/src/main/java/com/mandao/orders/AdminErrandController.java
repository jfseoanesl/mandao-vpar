package com.mandao.orders;

import com.mandao.orders.ErrandDtos.AssignRequest;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.PriceRequest;
import com.mandao.orders.ErrandDtos.ReasonRequest;
import com.mandao.shared.AuthUser;
import com.mandao.shared.FileResponses;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class AdminErrandController {

    private final AdminErrandService service;

    public AdminErrandController(AdminErrandService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/errands")
    public List<ErrandSummary> list(@RequestParam(value = "status", required = false) ErrandStatus status) {
        return service.list(status);
    }

    @PostMapping("/api/admin/errands/{id}/assign")
    public ErrandView assign(@PathVariable("id") UUID id, @Valid @RequestBody AssignRequest request) {
        return service.assign(id, request.courierId(), AuthUser.current().id());
    }

    @PostMapping("/api/admin/errands/{id}/cancel")
    public ErrandView cancel(@PathVariable("id") UUID id, @Valid @RequestBody ReasonRequest request) {
        return service.cancel(id, request.reason(), AuthUser.current().id());
    }

    @PutMapping("/api/admin/errands/{id}/price")
    public ErrandView price(@PathVariable("id") UUID id, @Valid @RequestBody PriceRequest request) {
        return service.adjustPrice(id, request.amount(), AuthUser.current().id());
    }

    @PostMapping("/api/admin/errands/{id}/resolve")
    public ErrandView resolve(@PathVariable("id") UUID id) {
        return service.resolveAttention(id, AuthUser.current().id());
    }

    @PostMapping("/api/admin/errands/{id}/courier-paid")
    public ErrandView courierPaid(@PathVariable("id") UUID id) {
        return service.markCourierPaid(id, AuthUser.current().id());
    }

    @GetMapping("/api/admin/export/errands")
    public ResponseEntity<byte[]> export(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = service.exportCsv(from.atStartOfDay(AdminErrandService.BOGOTA).toInstant(),
                to.plusDays(1).atStartOfDay(AdminErrandService.BOGOTA).toInstant());
        return FileResponses.csv(csv, "mandados-" + from + "-a-" + to + ".csv");
    }
}
