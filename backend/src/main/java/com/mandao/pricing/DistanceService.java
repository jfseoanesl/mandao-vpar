package com.mandao.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.shared.Geo;
import com.mandao.shared.MandaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Distancia de ruta con OSRM; si no responde en 3 s, haversine × 1,3 (degradación elegante, P6). */
@Service
public class DistanceService {

    private static final Logger log = LoggerFactory.getLogger(DistanceService.class);
    static final double HAVERSINE_ROUTE_FACTOR = 1.3;

    private final RestClient client;
    private final boolean enabled;

    public DistanceService(MandaoProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(props.routing().osrmUrl()).requestFactory(factory).build();
        this.enabled = props.routing().enabled();
    }

    public double routeKm(List<GeoPoint> points) {
        if (points.size() < 2) {
            return 0;
        }
        if (enabled) {
            try {
                String coords = points.stream()
                        .map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.lng(), p.lat()))
                        .collect(Collectors.joining(";"));
                JsonNode body = client.get()
                        .uri("/route/v1/driving/{coords}?overview=false", coords)
                        .retrieve().body(JsonNode.class);
                if (body != null && "Ok".equals(body.path("code").asText())) {
                    return body.path("routes").get(0).path("distance").asDouble() / 1000.0;
                }
            } catch (Exception e) {
                log.warn("OSRM no disponible, usando distancia estimada: {}", e.getMessage());
            }
        }
        return straightLineKm(points) * HAVERSINE_ROUTE_FACTOR;
    }

    static double straightLineKm(List<GeoPoint> points) {
        double km = 0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1);
            GeoPoint b = points.get(i);
            km += Geo.haversineKm(a.lat(), a.lng(), b.lat(), b.lng());
        }
        return km;
    }
}
