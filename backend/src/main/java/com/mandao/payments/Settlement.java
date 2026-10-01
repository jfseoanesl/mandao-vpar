package com.mandao.payments;

import java.util.ArrayList;
import java.util.List;

/** Cálculo puro de la liquidación de un mandado (AC-007-03). */
public final class Settlement {

    public record Line(LedgerEntry.Type type, long amount) {
    }

    private Settlement() {
    }

    public static List<Line> compute(long purchaseBudget, long purchases, long platformFee, long courierPayout) {
        List<Line> lines = new ArrayList<>();
        long refund = purchaseBudget - purchases;
        if (refund > 0) {
            lines.add(new Line(LedgerEntry.Type.REFUND, refund));
        }
        lines.add(new Line(LedgerEntry.Type.COURIER_PAYOUT, courierPayout));
        lines.add(new Line(LedgerEntry.Type.PLATFORM_FEE, platformFee));
        return lines;
    }
}
