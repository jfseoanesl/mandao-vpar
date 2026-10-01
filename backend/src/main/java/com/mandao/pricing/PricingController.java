package com.mandao.pricing;

import com.mandao.analytics.AnalyticsService;
import com.mandao.pricing.PricingDtos.QuoteRequest;
import com.mandao.pricing.PricingDtos.TariffRequest;
import com.mandao.pricing.PricingDtos.TariffView;
import com.mandao.shared.AuthUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PricingController {

    private final PricingService pricing;
    private final AnalyticsService analytics;

    public PricingController(PricingService pricing, AnalyticsService analytics) {
        this.pricing = pricing;
        this.analytics = analytics;
    }

    /** AC-002-03 y AC-002-09. */
    @PostMapping("/api/quotes")
    public PricingEngine.Breakdown quote(@Valid @RequestBody QuoteRequest request) {
        PricingEngine.Breakdown quote = pricing.quote(request.stops(), request.estimatedWaitMinutes(),
                request.complexity());
        analytics.track("quote_viewed", AuthUser.current().id(), null, String.valueOf(quote.total()), null);
        return quote;
    }

    @GetMapping("/api/admin/tariff")
    public TariffView tariff() {
        return TariffView.of(pricing.activeTariff());
    }

    @PutMapping("/api/admin/tariff")
    public TariffView updateTariff(@Valid @RequestBody TariffRequest request) {
        return TariffView.of(pricing.newVersion(request, AuthUser.current().id()));
    }
}
