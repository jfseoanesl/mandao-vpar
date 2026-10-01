package com.mandao.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 003 · mandado negociable. */
class NegotiationIT extends IntegrationTest {

    @Test
    @DisplayName("AC-003-01..04 · ofertas, una por mandadero, el cliente acepta y se rechazan las demás")
    void acceptOffer() throws Exception {
        Session client = client();
        Session a = approvedCourier(10.4635, -73.2530);
        Session b = approvedCourier(10.4640, -73.2520);
        JsonNode errand = createErrand(client, errandBody("BUSCAR_COMPRAR", "NEGOTIABLE", 350_000L));
        String id = errand.get("id").asText();
        assertThat(errand.get("status").asText()).isEqualTo("NEGOTIATING");
        assertThat(errand.get("quotedPrice").asLong()).isPositive();

        // No se puede aceptar directo un negociable
        perform(post("/api/courier/errands/" + id + "/accept"), a).andExpect(status().isConflict());

        perform(post("/api/courier/errands/" + id + "/bid"), a, Map.of("amount", 18_000, "message", "Conozco el sitio"))
                .andExpect(status().isCreated());
        perform(post("/api/courier/errands/" + id + "/bid"), a, Map.of("amount", 17_000))
                .andExpect(status().isConflict());
        perform(post("/api/courier/errands/" + id + "/bid"), a, Map.of("amount", 1_000))
                .andExpect(status().isBadRequest());
        perform(post("/api/courier/errands/" + id + "/bid"), b, Map.of("amount", 20_000))
                .andExpect(status().isCreated());

        JsonNode offers = getJson("/api/errands/" + id + "/offers", client);
        assertThat(offers).hasSize(2);
        assertThat(offers.get(0).at("/courier/fullName").asText()).isNotBlank();
        assertThat(offers.get(0).at("/courier/phone").isMissingNode()).isTrue();
        String offerA = offers.get(0).get("id").asText();

        perform(post("/api/errands/" + id + "/offers/" + offerA + "/accept"), client)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.totalPrice").value(18_000))
                .andExpect(jsonPath("$.platformFee").value(4_100))
                .andExpect(jsonPath("$.courierPayout").value(13_900))
                .andExpect(jsonPath("$.courier.id").value(a.id().toString()));

        JsonNode after = getJson("/api/errands/" + id + "/offers", client);
        assertThat(after.findValuesAsText("status")).containsExactlyInAnyOrder("ACCEPTED", "REJECTED");
    }

    @Test
    @DisplayName("AC-003-05 · contraoferta aceptada por el mandadero")
    void counterOffer() throws Exception {
        Session client = client();
        Session courier = approvedCourier(10.4635, -73.2530);
        String id = createErrand(client, errandBody("OTRO", "NEGOTIABLE", null)).get("id").asText();
        perform(post("/api/courier/errands/" + id + "/bid"), courier, Map.of("amount", 25_000))
                .andExpect(status().isCreated());
        String offerId = getJson("/api/errands/" + id + "/offers", client).get(0).get("id").asText();

        perform(post("/api/errands/" + id + "/offers/" + offerId + "/counter"), client, Map.of("amount", 20_000))
                .andExpect(jsonPath("$.status").value("COUNTERED"))
                .andExpect(jsonPath("$.counterAmount").value(20_000));

        JsonNode opp = getJson("/api/courier/opportunities", courier);
        boolean seesCounter = false;
        for (JsonNode o : opp) {
            if (o.get("id").asText().equals(id)) {
                seesCounter = o.at("/myOffer/status").asText().equals("COUNTERED");
            }
        }
        assertThat(seesCounter).isTrue();

        perform(post("/api/courier/errands/" + id + "/accept-counter"), courier)
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.totalPrice").value(20_000));
        perform(get("/api/errands/" + id), client).andExpect(jsonPath("$.courier.phone").exists());
    }
}
