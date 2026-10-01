package com.mandao.orders;

import java.util.EnumSet;
import java.util.Set;

public enum ErrandStatus {
    NEGOTIATING, SEARCHING, ASSIGNED, GOING_TO_ORIGIN, IN_PROGRESS, DELIVERED, COMPLETED, RATED, CANCELLED, FAILED;

    public static final Set<ErrandStatus> OPEN = EnumSet.of(SEARCHING, NEGOTIATING);
    public static final Set<ErrandStatus> COURIER_ACTIVE = EnumSet.of(ASSIGNED, GOING_TO_ORIGIN, IN_PROGRESS);
    public static final Set<ErrandStatus> CLIENT_CANCELLABLE = EnumSet.of(SEARCHING, NEGOTIATING, ASSIGNED,
            GOING_TO_ORIGIN);
    public static final Set<ErrandStatus> EVIDENCE_ALLOWED = EnumSet.of(GOING_TO_ORIGIN, IN_PROGRESS, DELIVERED);
    public static final Set<ErrandStatus> DONE = EnumSet.of(COMPLETED, RATED);
    public static final Set<ErrandStatus> FINAL = EnumSet.of(COMPLETED, RATED, CANCELLED, FAILED);
}
