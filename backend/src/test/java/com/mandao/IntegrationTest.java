package com.mandao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.couriers.CourierEnums.PayoutMethod;
import com.mandao.couriers.CourierEnums.VehicleType;
import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.identity.RateLimiter;
import com.mandao.identity.Role;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de pruebas de integración: Spring Boot completo + PostgreSQL/PostGIS real en Testcontainers.
 * Un solo contenedor compartido por todas las clases (arranque rápido).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    public static final String PASSWORD = "Clave2026segura";
    /** Centro de Valledupar y un destino a ~1,5 km. */
    public static final double[] ORIGIN = {10.4631, -73.2532};
    public static final double[] DESTINATION = {10.4735, -73.2436};

    public record Session(String token, UUID id, String phone, Cookie refreshCookie) {
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected CourierProfileRepository couriers;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected RateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimits() {
        rateLimiter.reset();
    }

    protected static String randomPhone() {
        return "3" + String.format("%09d", ThreadLocalRandom.current().nextInt(0, 1_000_000_000));
    }

    protected Session register(String role, String name) throws Exception {
        String phone = randomPhone();
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", name, "phone", phone, "password", PASSWORD,
                                "role", role, "acceptPrivacy", true, "utmSource", "test"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = read(res);
        return new Session(body.get("accessToken").asText(), UUID.fromString(body.at("/user/id").asText()), phone,
                res.getResponse().getCookie("mandao_rt"));
    }

    protected Session client() throws Exception {
        return register("CLIENT", "Cliente Prueba");
    }

    /** Mandadero aprobado, disponible y ubicado en el punto dado. */
    protected Session approvedCourier(double lat, double lng) throws Exception {
        Session s = register("COURIER", "Mandadero Prueba");
        CourierProfile p = couriers.findById(s.id()).orElseThrow();
        p.updateDetails(VehicleType.MOTO, "ABC12D", PayoutMethod.NEQUI, "3001234567");
        p.approve();
        p.setAvailable(true);
        p.updateLocation(lat, lng);
        couriers.save(p);
        return s;
    }

    protected Session admin() throws Exception {
        String phone = randomPhone();
        users.save(new User("Admin Prueba", phone, null, encoder.encode(PASSWORD), Role.ADMIN, null, null));
        return login(phone);
    }

    protected Session login(String phone) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = read(res);
        return new Session(body.get("accessToken").asText(), UUID.fromString(body.at("/user/id").asText()), phone,
                res.getResponse().getCookie("mandao_rt"));
    }

    /** AC-001-14: acepta los términos vigentes y devuelve la sesión nueva (token sin la marca de pendiente). */
    protected Session acceptTerms(Session s) throws Exception {
        MvcResult res = perform(post("/api/me/terms"), s, Map.of("version", com.mandao.identity.AuthDtos.TERMS_VERSION))
                .andExpect(status().isOk()).andReturn();
        return new Session(read(res).get("accessToken").asText(), s.id(), s.phone(),
                res.getResponse().getCookie("mandao_rt"));
    }

    protected Map<String, Object> errandBody(String category, String mode, Long budget) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("category", category);
        body.put("description", "Comprar medicamentos en la droguería y traerlos a la casa");
        body.put("complexity", "MEDIA");
        body.put("pricingMode", mode);
        body.put("estimatedWaitMinutes", 0);
        body.put("paymentMethod", "NEQUI");
        body.put("stops", List.of(
                Map.of("address", "Droguería Centro, Valledupar", "lat", ORIGIN[0], "lng", ORIGIN[1]),
                Map.of("address", "Casa del cliente, Valledupar", "lat", DESTINATION[0], "lng", DESTINATION[1],
                        "contactName", "Ana", "contactPhone", "3009998877")));
        if (budget != null) {
            body.put("purchaseBudget", budget);
        }
        return body;
    }

    protected JsonNode createErrand(Session client, Map<String, Object> body) throws Exception {
        return read(perform(post("/api/errands"), client, body).andExpect(status().isCreated()).andReturn());
    }

    protected ResultActions perform(MockHttpServletRequestBuilder builder, Session session, Object body)
            throws Exception {
        if (session != null) {
            builder.header("Authorization", "Bearer " + session.token());
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mvc.perform(builder);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder builder, Session session) throws Exception {
        return perform(builder, session, null);
    }

    protected JsonNode getJson(String url, Session session) throws Exception {
        return read(perform(get(url), session).andExpect(status().isOk()).andReturn());
    }

    protected String json(Object o) throws Exception {
        return mapper.writeValueAsString(o);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
