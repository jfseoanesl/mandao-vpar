package com.mandao.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** AC-005-08: si el cliente no confirma en 2 horas, el mandado entregado se completa solo. */
@Component
public class AutoConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmJob.class);
    static final Duration AUTO_CONFIRM_AFTER = Duration.ofHours(2);

    private final ErrandRepository errands;
    private final ErrandLifecycle lifecycle;

    public AutoConfirmJob(ErrandRepository errands, ErrandLifecycle lifecycle) {
        this.errands = errands;
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(AUTO_CONFIRM_AFTER);
        for (Errand e : errands.findByStatusAndDeliveredAtBefore(ErrandStatus.DELIVERED, cutoff)) {
            lifecycle.transition(e, ErrandStatus.COMPLETED, null, "Confirmación automática (2 h)");
            log.info("Mandado {} confirmado automáticamente", e.getCode());
        }
    }
}
