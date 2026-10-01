package com.mandao.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** KPIs de validación del piloto (spec 010). Consultas SQL directas: rápidas y auditables. */
@Service
public class KpiService {

    public record Kpi(String key, String label, Double value, double target, String unit, boolean higherIsBetter,
                      Boolean met) {
        static Kpi of(String key, String label, Double value, double target, String unit, boolean higher) {
            Boolean met = value == null ? null : higher ? value >= target : value <= target;
            return new Kpi(key, label, value, target, unit, higher, met);
        }
    }

    public record Totals(long errands, long completed, long cancelled, long failed, long gmv, long platformRevenue,
                         long activeClients, long signups, long activeCouriers) {
    }

    public record Funnel(long quotes, long requested, long completed) {
    }

    public record DayPoint(LocalDate date, long errands, long completed, long gmv) {
    }

    public record Breakdown(String key, long errands, long completed) {
    }

    public record KpiReport(Instant from, Instant to, long days, List<Kpi> kpis, Totals totals, Funnel funnel,
                            List<DayPoint> daily, List<Breakdown> byCategory, List<Breakdown> bySegment,
                            List<Breakdown> byChannel) {
    }

    private static final String DONE = "status IN ('COMPLETED','RATED')";

    private final JdbcTemplate jdbc;

    public KpiService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public KpiReport report(Instant from, Instant to) {
        Timestamp f = Timestamp.from(from);
        Timestamp t = Timestamp.from(to);
        long days = Math.max(1, Duration.between(from, to).toDays());

        var base = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE %1$s) AS completed,
                       count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled,
                       count(*) FILTER (WHERE status = 'FAILED'
                                           OR (status = 'CANCELLED' AND assigned_at IS NOT NULL)) AS failed_after_assign,
                       avg(total_price) FILTER (WHERE %1$s) AS avg_ticket,
                       avg(extract(epoch FROM assigned_at - created_at) / 60)
                           FILTER (WHERE assigned_at IS NOT NULL AND scheduled_for IS NULL) AS avg_assign_min,
                       coalesce(sum(total_price) FILTER (WHERE %1$s), 0) AS gmv,
                       coalesce(sum(platform_fee) FILTER (WHERE %1$s), 0) AS revenue,
                       count(DISTINCT client_id) AS clients,
                       count(DISTINCT courier_id) AS couriers
                FROM errands WHERE created_at >= ? AND created_at < ?
                """.formatted(DONE), f, t);

        long total = num(base.get("total")).longValue();
        long completed = num(base.get("completed")).longValue();
        long failedAfterAssign = num(base.get("failed_after_assign")).longValue();

        var repeat = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE n >= 2) AS repeaters, count(*) AS clients
                FROM (SELECT client_id, count(*) AS n FROM errands
                      WHERE created_at >= ? AND created_at < ? AND status <> 'CANCELLED'
                      GROUP BY client_id) x
                """, f, t);
        long clients = num(repeat.get("clients")).longValue();

        Double avgRating = toDouble(jdbc.queryForObject("""
                SELECT avg(r.stars) FROM ratings r JOIN errands e ON e.id = r.errand_id
                WHERE e.created_at >= ? AND e.created_at < ?
                """, Object.class, f, t));

        List<Kpi> kpis = new ArrayList<>();
        kpis.add(Kpi.of("errandsPerDay", "Mandados por día", round((double) total / days, 1), 15, "mandados", true));
        kpis.add(Kpi.of("avgTicket", "Ticket medio", round(toDouble(base.get("avg_ticket")), 0), 12000, "COP", true));
        kpis.add(Kpi.of("assignMinutes", "Tiempo de asignación", round(toDouble(base.get("avg_assign_min")), 1), 5,
                "min", false));
        kpis.add(Kpi.of("completionRate", "Servicios completados",
                completed + failedAfterAssign == 0 ? null
                        : round(100.0 * completed / (completed + failedAfterAssign), 1), 95, "%", true));
        kpis.add(Kpi.of("avgRating", "Calificación", round(avgRating, 2), 4.6, "estrellas", true));
        kpis.add(Kpi.of("repeatRate", "Recompra",
                clients == 0 ? null : round(100.0 * num(repeat.get("repeaters")).longValue() / clients, 1), 30,
                "%", true));

        long signups = count("SELECT count(*) FROM users WHERE created_at >= ? AND created_at < ?", f, t);
        Totals totals = new Totals(total, completed, num(base.get("cancelled")).longValue(), failedAfterAssign,
                num(base.get("gmv")).longValue(), num(base.get("revenue")).longValue(),
                num(base.get("clients")).longValue(), signups, num(base.get("couriers")).longValue());

        Funnel funnel = new Funnel(
                count("SELECT count(*) FROM analytics_events WHERE name = 'quote_viewed' AND created_at >= ? AND created_at < ?", f, t),
                count("SELECT count(*) FROM analytics_events WHERE name = 'errand_requested' AND created_at >= ? AND created_at < ?", f, t),
                completed);

        List<DayPoint> daily = jdbc.query("""
                SELECT (created_at AT TIME ZONE 'America/Bogota')::date AS d, count(*) AS n,
                       count(*) FILTER (WHERE %1$s) AS c,
                       coalesce(sum(total_price) FILTER (WHERE %1$s), 0) AS g
                FROM errands WHERE created_at >= ? AND created_at < ?
                GROUP BY d ORDER BY d
                """.formatted(DONE), (rs, i) -> new DayPoint(rs.getDate("d").toLocalDate(), rs.getLong("n"),
                rs.getLong("c"), rs.getLong("g")), f, t);

        List<Breakdown> byCategory = breakdown("category", f, t);
        List<Breakdown> bySegment = breakdown("CASE WHEN company_id IS NULL THEN 'PERSONA' ELSE 'EMPRESA' END", f, t);
        List<Breakdown> byChannel = jdbc.query("""
                SELECT coalesce(u.acquisition_source, 'directo') AS k, count(*) AS n,
                       count(*) FILTER (WHERE e.status IN ('COMPLETED','RATED')) AS c
                FROM errands e JOIN users u ON u.id = e.client_id
                WHERE e.created_at >= ? AND e.created_at < ?
                GROUP BY k ORDER BY n DESC
                """, (rs, i) -> new Breakdown(rs.getString("k"), rs.getLong("n"), rs.getLong("c")), f, t);

        return new KpiReport(from, to, days, kpis, totals, funnel, daily, byCategory, bySegment, byChannel);
    }

    private List<Breakdown> breakdown(String expression, Timestamp f, Timestamp t) {
        return jdbc.query("""
                SELECT %1$s AS k, count(*) AS n, count(*) FILTER (WHERE %2$s) AS c
                FROM errands WHERE created_at >= ? AND created_at < ?
                GROUP BY k ORDER BY n DESC
                """.formatted(expression, DONE),
                (rs, i) -> new Breakdown(rs.getString("k"), rs.getLong("n"), rs.getLong("c")), f, t);
    }

    private long count(String sql, Timestamp f, Timestamp t) {
        Long n = jdbc.queryForObject(sql, Long.class, f, t);
        return n == null ? 0 : n;
    }

    private static Number num(Object o) {
        return o == null ? 0 : (Number) o;
    }

    private static Double toDouble(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private static Double round(Double v, int decimals) {
        if (v == null) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(v * factor) / factor;
    }
}
