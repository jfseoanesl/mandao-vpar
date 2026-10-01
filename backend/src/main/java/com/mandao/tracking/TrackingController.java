package com.mandao.tracking;

import com.mandao.shared.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TrackingController {

    public record LocationRequest(
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lng,
            @DecimalMin("0") @DecimalMax("100000") Double accuracy) {
    }

    private final TrackingService tracking;

    public TrackingController(TrackingService tracking) {
        this.tracking = tracking;
    }

    @PostMapping("/api/courier/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ping(@Valid @RequestBody LocationRequest request) {
        tracking.ping(AuthUser.current().id(), request.lat(), request.lng(), request.accuracy());
    }

    @GetMapping("/api/errands/{id}/track")
    public List<TrackingService.LivePoint> track(@PathVariable("id") UUID id) {
        return tracking.track(id, AuthUser.current());
    }

    @GetMapping("/api/admin/live")
    public TrackingService.LiveMap live() {
        return tracking.liveMap();
    }
}
