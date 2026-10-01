package com.mandao.shared;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Los trabajos programados (despacho, auto-confirmación, purga) se pueden apagar en pruebas. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "mandao.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
