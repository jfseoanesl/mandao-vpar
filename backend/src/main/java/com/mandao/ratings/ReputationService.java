package com.mandao.ratings;

import com.mandao.identity.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Reputación del mandadero: no solo estrellas (AC-008-03). */
@Service
public class ReputationService {

    public record Reputation(Double averageStars, long ratingsCount, long completedCount, Double completionRate,
                             Double punctualityRate, Instant memberSince, Map<String, Long> tags) {
    }

    /**
     * Puntualidad: entregado en ≤ estimado + 15 min, con estimado = 15 min de alistamiento
     * + 3 min/km (≈ 20 km/h urbano) + espera declarada.
     */
    private static final String STATS_SQL = """
            SELECT count(*) FILTER (WHERE status IN ('COMPLETED', 'RATED'))                          AS completed,
                   count(*) FILTER (WHERE status = 'FAILED'
                                       OR (status = 'CANCELLED' AND assigned_at IS NOT NULL))       AS failed,
                   count(*) FILTER (WHERE delivered_at IS NOT NULL)                                  AS delivered,
                   count(*) FILTER (WHERE delivered_at IS NOT NULL
                                      AND extract(epoch FROM delivered_at - assigned_at) / 60
                                          <= 15 + distance_km * 3 + estimated_wait_min + 15)         AS on_time
            FROM errands WHERE courier_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final RatingRepository ratings;
    private final UserRepository users;

    public ReputationService(JdbcTemplate jdbc, RatingRepository ratings, UserRepository users) {
        this.jdbc = jdbc;
        this.ratings = ratings;
        this.users = users;
    }

    public Reputation of(UUID courierId) {
        var list = ratings.findByCourierId(courierId);
        Double avg = list.isEmpty() ? null
                : Math.round(list.stream().mapToInt(Rating::getStars).average().orElse(0) * 10) / 10.0;
        Map<String, Long> tags = new TreeMap<>();
        list.stream().map(Rating::getTags).filter(t -> t != null && !t.isBlank())
                .flatMap(t -> Arrays.stream(t.split(",")))
                .forEach(t -> tags.merge(t, 1L, Long::sum));

        return jdbc.query(STATS_SQL, rs -> {
            rs.next();
            long completed = rs.getLong("completed");
            long failed = rs.getLong("failed");
            long delivered = rs.getLong("delivered");
            long onTime = rs.getLong("on_time");
            Double completion = completed + failed == 0 ? null : ratio(completed, completed + failed);
            Double punctuality = delivered == 0 ? null : ratio(onTime, delivered);
            Instant since = users.findById(courierId).map(u -> u.getCreatedAt()).orElse(null);
            return new Reputation(avg, list.size(), completed, completion, punctuality, since, tags);
        }, courierId);
    }

    private static double ratio(long a, long b) {
        return Math.round(1000.0 * a / b) / 10.0;
    }
}
