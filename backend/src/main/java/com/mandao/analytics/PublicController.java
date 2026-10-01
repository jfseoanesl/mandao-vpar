package com.mandao.analytics;

import com.mandao.shared.AuthUser;
import com.mandao.shared.MandaoProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Endpoints públicos: configuración para la landing y eventos del embudo desde el navegador. */
@RestController
public class PublicController {

    /** Solo se aceptan eventos conocidos desde el cliente; el resto los registra el servidor. */
    static final Set<String> CLIENT_EVENTS = Set.of("landing_view", "whatsapp_click", "signup_started",
            "install_prompt", "category_selected");

    public record PublicConfig(String whatsapp, double centerLat, double centerLng, double radiusKm) {
    }

    public record EventRequest(@NotBlank @Size(max = 40) String name, @Size(max = 300) String props,
                               @Size(max = 60) String utmSource) {
    }

    private final MandaoProperties props;
    private final AnalyticsService analytics;

    public PublicController(MandaoProperties props, AnalyticsService analytics) {
        this.props = props;
        this.analytics = analytics;
    }

    @GetMapping("/api/public/config")
    public PublicConfig config() {
        var area = props.serviceArea();
        return new PublicConfig(props.contact().whatsapp(), area.centerLat(), area.centerLng(), area.radiusKm());
    }

    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void event(@Valid @RequestBody EventRequest request) {
        if (!CLIENT_EVENTS.contains(request.name())) {
            return;
        }
        AuthUser user = AuthUser.currentOrNull();
        analytics.track(request.name(), user == null ? null : user.id(), null, request.props(), request.utmSource());
    }
}
