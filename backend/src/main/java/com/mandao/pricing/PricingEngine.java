package com.mandao.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor de precios puro (sin estado ni I/O) — spec 002.
 * <pre>
 * subtotal = base + km×tarifaKm + max(0, espera−incluida)×tarifaMin + max(0, paradas−2)×tarifaParada + complejidad
 * total    = redondeo hacia arriba a $100
 * </pre>
 */
public final class PricingEngine {

    public record Split(long platformFee, long courierPayout) {
    }

    public record Breakdown(double distanceKm, long base, long distance, long waitCharge, long extraStops, long complexity,
                            long total, long platformFee, long courierPayout, int tariffVersion) {
    }

    private PricingEngine() {
    }

    public static Breakdown quote(Tariff t, double distanceKm, int stops, int waitMinutes, Complexity complexity) {
        double km = BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        long base = t.getBaseFare();
        long distance = BigDecimal.valueOf(km).multiply(BigDecimal.valueOf(t.getPerKm()))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        long waitCharge = Math.max(0, waitMinutes - t.getIncludedWaitMin()) * t.getPerExtraMin();
        long extraStops = Math.max(0, stops - 2) * t.getPerExtraStop();
        long complexitySurcharge = t.complexitySurcharge(complexity);
        long total = roundUpTo100(base + distance + waitCharge + extraStops + complexitySurcharge);
        Split split = split(total, t.getCommissionPct());
        return new Breakdown(km, base, distance, waitCharge, extraStops, complexitySurcharge, total, split.platformFee(),
                split.courierPayout(), t.getVersion());
    }

    /** Reparte un total entre plataforma (redondeado a $100) y mandadero. */
    public static Split split(long total, BigDecimal commissionPct) {
        long fee = BigDecimal.valueOf(total).multiply(commissionPct)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).longValue();
        return new Split(fee, total - fee);
    }

    static long roundUpTo100(long amount) {
        return ((amount + 99) / 100) * 100;
    }
}
