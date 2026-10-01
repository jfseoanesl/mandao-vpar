package com.mandao.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PricingDtos {

    private PricingDtos() {
    }

    public record QuoteRequest(
            @NotNull Complexity complexity,
            @NotNull @Size(min = 1, max = 6, message = "Entre 1 y 6 paradas") List<@Valid GeoPoint> stops,
            @Min(0) @Max(240) int estimatedWaitMinutes) {
    }

    public record TariffView(int version, long baseFare, long perKm, int includedWaitMin, long perExtraMin,
                             long perExtraStop, long complexityMedium, long complexityComplex,
                             BigDecimal commissionPct, Instant createdAt) {
        public static TariffView of(Tariff t) {
            return new TariffView(t.getVersion(), t.getBaseFare(), t.getPerKm(), t.getIncludedWaitMin(),
                    t.getPerExtraMin(), t.getPerExtraStop(), t.getComplexityMedium(), t.getComplexityComplex(),
                    t.getCommissionPct(), t.getCreatedAt());
        }
    }

    public record TariffRequest(
            @Min(0) @Max(100000) long baseFare,
            @Min(0) @Max(20000) long perKm,
            @Min(0) @Max(120) int includedWaitMin,
            @Min(0) @Max(5000) long perExtraMin,
            @Min(0) @Max(50000) long perExtraStop,
            @Min(0) @Max(100000) long complexityMedium,
            @Min(0) @Max(200000) long complexityComplex,
            @NotNull @DecimalMin("0.0") @DecimalMax("0.6") BigDecimal commissionPct) {
    }
}
