package com.example.auditoria.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkpoints del Paso 6 contra la aplicación completa (H2 en memoria). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HallazgoApiTest {

    static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mvc;

    String registrar(String titulo, String area, String severidad) throws Exception {
        String body = """
                {"titulo":"%s","descripcion":"Detectado en la auditoria de agosto",
                 "areaResponsable":"%s","severidad":"%s","fechaDeteccion":"2026-08-01"}
                """.formatted(titulo, area, severidad);
        String json = mvc.perform(post("/api/hallazgos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"hallazgoId\":\"([^\"]+)\".*", "$1");
    }

    void iniciarRemediacion(String id) throws Exception {
        mvc.perform(patch("/api/hallazgos/{id}/iniciar-remediacion", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsable\":\"Equipo de Infraestructura\",\"fechaLimite\":\"2026-08-20\",\"notas\":\"Rotar credenciales\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REMEDIACION"));
    }

    @Test
    void registrarDevuelve201ConUnUuid() throws Exception {
        String body = """
                {"titulo":"Contraseñas por defecto en servidor de pruebas",
                 "descripcion":"El servidor QA usa credenciales por defecto del fabricante",
                 "areaResponsable":"Infraestructura","severidad":"ALTA","fechaDeteccion":"2026-08-01"}
                """;
        mvc.perform(post("/api/hallazgos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hallazgoId").value(org.hamcrest.Matchers.matchesPattern(UUID_REGEX)));
    }

    @Test
    void iniciarRemediacionCambiaElEstado() throws Exception {
        String id = registrar("Credenciales por defecto", "Infraestructura", "ALTA");

        iniciarRemediacion(id);

        mvc.perform(get("/api/hallazgos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REMEDIACION"))
                .andExpect(jsonPath("$.plan.responsable").value("Equipo de Infraestructura"));
    }

    @Test
    void cerrarUnHallazgoAbiertoDevuelve400() throws Exception {
        String id = registrar("Backups sin cifrar", "Infraestructura", "MEDIA");

        mvc.perform(patch("/api/hallazgos/{id}/cerrar", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se puede cerrar un hallazgo sin plan de remediacion"));
    }

    @Test
    void reabrirUnHallazgoCerradoDevuelve200() throws Exception {
        String id = registrar("Cuentas sin MFA", "Seguridad", "CRITICA");
        iniciarRemediacion(id);
        mvc.perform(patch("/api/hallazgos/{id}/cerrar", id)).andExpect(status().isOk());

        mvc.perform(patch("/api/hallazgos/{id}/reabrir", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Aparecieron cuentas nuevas sin MFA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REABIERTO"));

        mvc.perform(get("/api/hallazgos/{id}", id))
                .andExpect(jsonPath("$.estado").value("REABIERTO"))
                .andExpect(jsonPath("$.fechaCierre").doesNotExist());
    }

    @Test
    void reabrirUnHallazgoAbiertoDevuelve400() throws Exception {
        String id = registrar("Logs sin retencion", "Operaciones", "BAJA");

        mvc.perform(patch("/api/hallazgos/{id}/reabrir", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se puede transicionar de ABIERTO a REABIERTO"));
    }

    @Test
    void unIdInexistenteDevuelve404YUnoMalFormado400() throws Exception {
        mvc.perform(get("/api/hallazgos/{id}", "6f1c2a7e-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/hallazgos/{id}", "no-es-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarSinTituloNiSeveridadDevuelve400() throws Exception {
        mvc.perform(post("/api/hallazgos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"areaResponsable\":\"TI\",\"fechaDeteccion\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.titulo").exists())
                .andExpect(jsonPath("$.severidad").exists());
    }

    @Test
    void listarDevuelveLosHallazgosRegistrados() throws Exception {
        registrar("Hallazgo A", "TI", "ALTA");
        registrar("Hallazgo B", "Finanzas", "BAJA");

        mvc.perform(get("/api/hallazgos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
