package com.mandao.analytics;

import org.springframework.stereotype.Service;

import java.util.UUID;

/** Registro de eventos del embudo de validación (P1). */
@Service
public class AnalyticsService {

    private final AnalyticsEventRepository repository;

    public AnalyticsService(AnalyticsEventRepository repository) {
        this.repository = repository;
    }

    public void track(String name, UUID userId, UUID errandId, String props, String utmSource) {
        repository.save(new AnalyticsEvent(name, userId, errandId, props, utmSource));
    }
}
