package com.mandao.couriers;

import com.mandao.couriers.CourierDtos.AvailabilityRequest;
import com.mandao.couriers.CourierDtos.CourierView;
import com.mandao.couriers.CourierDtos.RejectRequest;
import com.mandao.couriers.CourierDtos.UpdateCourierRequest;
import com.mandao.couriers.CourierEnums.CourierStatus;
import com.mandao.couriers.CourierEnums.DocumentType;
import com.mandao.shared.AuthUser;
import com.mandao.shared.FileResponses;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
public class CourierController {

    private final CourierService service;

    public CourierController(CourierService service) {
        this.service = service;
    }

    // ---- Mandadero ----

    @GetMapping("/api/courier/me")
    public CourierView me() {
        return service.view(AuthUser.current().id());
    }

    @PutMapping("/api/courier/me")
    public CourierView update(@Valid @RequestBody UpdateCourierRequest request) {
        return service.updateDetails(AuthUser.current().id(), request);
    }

    @PostMapping(value = "/api/courier/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourierView uploadDocument(@RequestParam("type") DocumentType type,
                                      @RequestPart("file") MultipartFile file) {
        return service.uploadDocument(AuthUser.current().id(), type, file);
    }

    @PutMapping("/api/courier/availability")
    public CourierView availability(@RequestBody AvailabilityRequest request) {
        return service.setAvailability(AuthUser.current().id(), request.available());
    }

    // ---- Administración (AC-009-03..06) ----

    @GetMapping("/api/admin/couriers")
    public List<CourierView> list(@RequestParam(value = "status", required = false) CourierStatus status) {
        return service.list(status);
    }

    @PostMapping("/api/admin/couriers/{id}/approve")
    public CourierView approve(@PathVariable("id") UUID id) {
        return service.approve(id, AuthUser.current().id());
    }

    @PostMapping("/api/admin/couriers/{id}/reject")
    public CourierView reject(@PathVariable("id") UUID id, @Valid @RequestBody RejectRequest request) {
        return service.reject(id, request.reason(), AuthUser.current().id());
    }

    @GetMapping("/api/admin/documents/{id}")
    public ResponseEntity<byte[]> document(@PathVariable("id") UUID id) {
        CourierService.DocumentFile file = service.documentFile(id);
        return FileResponses.inline(file.data(), file.contentType());
    }
}
