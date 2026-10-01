package com.mandao.orders;

import com.mandao.shared.ApiException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.mandao.orders.ErrandStatus.*;

/** Transiciones permitidas del mandado (spec 002 · Estados). Pura: sin dependencias. */
public final class ErrandStateMachine {

    private static final Map<ErrandStatus, Set<ErrandStatus>> ALLOWED = new EnumMap<>(ErrandStatus.class);

    static {
        ALLOWED.put(NEGOTIATING, EnumSet.of(ASSIGNED, CANCELLED));
        ALLOWED.put(SEARCHING, EnumSet.of(ASSIGNED, CANCELLED));
        ALLOWED.put(ASSIGNED, EnumSet.of(GOING_TO_ORIGIN, CANCELLED, FAILED));
        ALLOWED.put(GOING_TO_ORIGIN, EnumSet.of(IN_PROGRESS, CANCELLED, FAILED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(DELIVERED, FAILED));
        ALLOWED.put(DELIVERED, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED, EnumSet.of(RATED));
        ALLOWED.put(RATED, EnumSet.noneOf(ErrandStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(ErrandStatus.class));
        ALLOWED.put(FAILED, EnumSet.noneOf(ErrandStatus.class));
    }

    private ErrandStateMachine() {
    }

    public static boolean canTransition(ErrandStatus from, ErrandStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void check(ErrandStatus from, ErrandStatus to) {
        if (!canTransition(from, to)) {
            throw ApiException.conflict("El mandado está en estado " + from + " y no puede pasar a " + to);
        }
    }
}
