package com.mandao.couriers;

import com.mandao.couriers.CourierEnums.CourierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {

    List<CourierProfile> findByStatus(CourierStatus status);

    List<CourierProfile> findByAvailableTrueAndLastPingAtAfter(Instant since);

    /**
     * Mandaderos candidatos (AC de 004): aprobados, disponibles, con ping reciente, sin mandado activo
     * y dentro del radio, ordenados por cercanía. Usa el índice GiST de PostGIS.
     */
    @Query(value = """
            SELECT cp.user_id
            FROM courier_profiles cp
            WHERE cp.status = 'APPROVED'
              AND cp.available = true
              AND cp.last_ping_at > :since
              AND cp.lat IS NOT NULL AND cp.lng IS NOT NULL
              AND ST_DWithin(geography(ST_SetSRID(ST_MakePoint(cp.lng, cp.lat), 4326)),
                             geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)), :radiusMeters)
              AND NOT EXISTS (SELECT 1 FROM errands e
                              WHERE e.courier_id = cp.user_id
                                AND e.status IN ('ASSIGNED', 'GOING_TO_ORIGIN', 'IN_PROGRESS'))
            ORDER BY ST_Distance(geography(ST_SetSRID(ST_MakePoint(cp.lng, cp.lat), 4326)),
                                 geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)))
            LIMIT :maxResults
            """, nativeQuery = true)
    List<UUID> findNearbyCandidates(@Param("lat") double lat, @Param("lng") double lng,
                                    @Param("radiusMeters") double radiusMeters, @Param("since") Instant since,
                                    @Param("maxResults") int maxResults);
}
