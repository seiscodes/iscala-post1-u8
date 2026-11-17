package com.example.auditoria.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkpoints de la Parte 2: dashboard, historial y una fila nueva por transición. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardYHistorialApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String registrar(String area, String severidad) throws Exception {
        String json = mvc.perform(post("/api/hallazgos").header("X-Usuario", "auditor.lopez")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Hallazgo de prueba\",\"areaResponsable\":\"" + area
                                + "\",\"severidad\":\"" + severidad + "\",\"fechaDeteccion\":\"2026-08-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"hallazgoId\":\"([^\"]+)\".*", "$1");
    }

    private int filasHistorial(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM historial_cambios_estado WHERE hallazgo_id = ?", Integer.class, id);
    }

    @Test
    void cadaTransicionInsertaExactamenteUnaFila() throws Exception {
        String id = registrar("Infraestructura", "ALTA");
        assertEquals(1, filasHistorial(id));

        mvc.perform(patch("/api/hallazgos/{id}/iniciar-remediacion", id).header("X-Usuario", "jefe.infra")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsable\":\"Equipo de Infraestructura\",\"fechaLimite\":\"2026-08-20\"}"))
                .andExpect(status().isOk());
        assertEquals(2, filasHistorial(id));

        mvc.perform(patch("/api/hallazgos/{id}/cerrar", id).header("X-Usuario", "jefe.infra")).andExpect(status().isOk());
        assertEquals(3, filasHistorial(id));

        mvc.perform(patch("/api/hallazgos/{id}/reabrir", id).header("X-Usuario", "auditor.lopez")
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Reapareció en el escaneo\"}"))
                .andExpect(status().isOk());
        assertEquals(4, filasHistorial(id));

        mvc.perform(get("/api/hallazgos/{id}/historial", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].estadoNuevo").value("ABIERTO"))
                .andExpect(jsonPath("$[0].usuario").value("auditor.lopez"))
                .andExpect(jsonPath("$[1].estadoAnterior").value("ABIERTO"))
                .andExpect(jsonPath("$[1].estadoNuevo").value("EN_REMEDIACION"))
                .andExpect(jsonPath("$[1].usuario").value("jefe.infra"))
                .andExpect(jsonPath("$[2].estadoNuevo").value("CERRADO"))
                .andExpect(jsonPath("$[3].estadoAnterior").value("CERRADO"))
                .andExpect(jsonPath("$[3].estadoNuevo").value("REABIERTO"))
                .andExpect(jsonPath("$[3].motivo").value("Reapareció en el escaneo"));
    }

    @Test
    void unaTransicionInvalidaNoInsertaFilas() throws Exception {
        String id = registrar("Finanzas", "MEDIA");

        mvc.perform(patch("/api/hallazgos/{id}/cerrar", id)).andExpect(status().isBadRequest());

        assertEquals(1, filasHistorial(id));
    }

    @Test
    void elHistorialDeUnIdInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/hallazgos/{id}/historial", "6f1c2a7e-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elDashboardReflejaLosHallazgosRegistrados() throws Exception {
        registrar("TI", "ALTA");
        registrar("TI", "CRITICA");
        registrar("Finanzas", "ALTA");

        mvc.perform(get("/api/hallazgos/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSeveridad[?(@.categoria == 'ALTA')].total").value(2))
                .andExpect(jsonPath("$.porSeveridad[?(@.categoria == 'CRITICA')].total").value(1))
                .andExpect(jsonPath("$.porEstado[?(@.categoria == 'ABIERTO')].total").value(3))
                .andExpect(jsonPath("$.promedioDiasCierrePorArea").isEmpty());
    }
}
