package com.mandao.analytics;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private UUID userId;
    private UUID errandId;
    private String props;
    private String utmSource;
    private Instant createdAt;

    protected AnalyticsEvent() {
    }

    public AnalyticsEvent(String name, UUID userId, UUID errandId, String props, String utmSource) {
        this.name = name;
        this.userId = userId;
        this.errandId = errandId;
        this.props = truncate(props, 500);
        this.utmSource = truncate(utmSource, 60);
        this.createdAt = Instant.now();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(max, s.length()));
    }

    public String getName() {
        return name;
    }

    public UUID getErrandId() {
        return errandId;
    }
}
