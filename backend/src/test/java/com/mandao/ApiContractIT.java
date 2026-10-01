package com.mandao;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Principio P2 (SDD): las operaciones que expone el backend deben ser exactamente
 * las declaradas en specs/api/openapi.yaml.
 */
class ApiContractIT extends IntegrationTest {

    private static final Set<String> METHODS = Set.of("get", "post", "put", "delete", "patch");

    @Test
    @DisplayName("El backend implementa exactamente el contrato specs/api/openapi.yaml")
    @SuppressWarnings("unchecked")
    void implementationMatchesContract() throws Exception {
        Path spec = Path.of("..", "specs", "api", "openapi.yaml");
        assertThat(spec).exists();
        Map<String, Object> contract;
        try (InputStream in = Files.newInputStream(spec)) {
            contract = new Yaml().load(in);
        }
        Set<String> declared = new TreeSet<>();
        ((Map<String, Map<String, Object>>) contract.get("paths")).forEach((path, ops) -> ops.keySet().stream()
                .filter(METHODS::contains).forEach(m -> declared.add(m.toUpperCase() + " " + path)));

        JsonNode docs = read(mvc.perform(get("/v3/api-docs")).andReturn());
        Set<String> implemented = new TreeSet<>();
        docs.get("paths").fields().forEachRemaining(e -> e.getValue().fieldNames().forEachRemaining(m -> {
            if (METHODS.contains(m)) {
                implemented.add(m.toUpperCase() + " " + e.getKey());
            }
        }));

        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(implemented);
        Set<String> undeclared = new TreeSet<>(implemented);
        undeclared.removeAll(declared);
        assertThat(missing).as("Declaradas en el contrato pero no implementadas").isEmpty();
        assertThat(undeclared).as("Implementadas pero no declaradas en el contrato").isEmpty();
    }
}
