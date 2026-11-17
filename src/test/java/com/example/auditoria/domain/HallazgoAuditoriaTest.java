package com.example.auditoria.domain;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.domain.valueobject.PlanRemediacion;
import com.example.auditoria.domain.valueobject.Severidad;
import com.example.auditoria.domain.valueobject.TransicionInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** El agregado se prueba sin Spring: es Java puro. */
class HallazgoAuditoriaTest {

    private static final LocalDate DETECCION = LocalDate.of(2026, 8, 1);
    private static final PlanRemediacion PLAN =
            new PlanRemediacion("Equipo de Infraestructura", LocalDate.of(2026, 8, 20), "Rotar credenciales");

    private static HallazgoAuditoria nuevo() {
        return new HallazgoAuditoria(HallazgoId.nuevo(), "Contraseñas por defecto en servidor de pruebas",
                "El servidor QA usa credenciales del fabricante", "Infraestructura", Severidad.ALTA, DETECCION);
    }

    @Test
    void unHallazgoNuevoEstaAbiertoYSinPlan() {
        HallazgoAuditoria h = nuevo();

        assertEquals(EstadoHallazgo.ABIERTO, h.getEstado());
        assertNull(h.getPlanRemediacion());
    }

    @Test
    void recorreElCicloCompletoYDevuelveElEstadoAnterior() {
        HallazgoAuditoria h = nuevo();

        assertEquals(EstadoHallazgo.ABIERTO, h.iniciarRemediacion(PLAN));
        assertEquals(EstadoHallazgo.EN_REMEDIACION, h.cerrar(LocalDate.of(2026, 8, 18)));
        assertEquals(LocalDate.of(2026, 8, 18), h.getFechaCierre());
        assertEquals(EstadoHallazgo.CERRADO, h.reabrir());
        assertNull(h.getFechaCierre());
        assertEquals(EstadoHallazgo.REABIERTO, h.iniciarRemediacion(PLAN));
        assertEquals(EstadoHallazgo.EN_REMEDIACION, h.getEstado());
    }

    @Test
    void noSePuedeCerrarUnHallazgoAbiertoSinPlan() {
        HallazgoAuditoria h = nuevo();

        assertThrows(IllegalStateException.class, () -> h.cerrar(LocalDate.of(2026, 8, 5)));
        assertEquals(EstadoHallazgo.ABIERTO, h.getEstado());
    }

    @Test
    void noSePuedeReabrirUnHallazgoQueSigueAbierto() {
        HallazgoAuditoria h = nuevo();

        TransicionInvalidaException ex = assertThrows(TransicionInvalidaException.class, h::reabrir);
        assertEquals("No se puede transicionar de ABIERTO a REABIERTO", ex.getMessage());
    }

    @Test
    void noSePuedeIniciarRemediacionDosVeces() {
        HallazgoAuditoria h = nuevo();
        h.iniciarRemediacion(PLAN);

        assertThrows(TransicionInvalidaException.class, () -> h.iniciarRemediacion(PLAN));
    }

    @Test
    void laFechaDeCierreNoPuedeSerAnteriorALaDeteccion() {
        HallazgoAuditoria h = nuevo();
        h.iniciarRemediacion(PLAN);

        assertThrows(IllegalArgumentException.class, () -> h.cerrar(LocalDate.of(2026, 7, 30)));
        assertEquals(EstadoHallazgo.EN_REMEDIACION, h.getEstado());
    }

    @Test
    void unPlanSinResponsableEsInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlanRemediacion(" ", LocalDate.of(2026, 9, 1), null));
    }

    @Test
    void elTituloYElAreaSonObligatorios() {
        assertThrows(IllegalArgumentException.class,
                () -> new HallazgoAuditoria(HallazgoId.nuevo(), "", null, "TI", Severidad.BAJA, DETECCION));
        assertThrows(IllegalArgumentException.class,
                () -> new HallazgoAuditoria(HallazgoId.nuevo(), "Titulo", null, null, Severidad.BAJA, DETECCION));
    }

    @Test
    void reconstituirConservaEstadoReabiertoSinRecorrerTransiciones() {
        HallazgoAuditoria h = HallazgoAuditoria.reconstituir(HallazgoId.nuevo(), "T", "D", "TI", Severidad.MEDIA,
                DETECCION, EstadoHallazgo.REABIERTO, PLAN, null);

        assertEquals(EstadoHallazgo.REABIERTO, h.getEstado());
        assertEquals(PLAN, h.getPlanRemediacion());
        assertEquals(EstadoHallazgo.REABIERTO, h.iniciarRemediacion(PLAN));
    }

    @Test
    void reconstituirConservaLaFechaDeCierreGuardada() {
        LocalDate cierre = LocalDate.of(2026, 8, 10);

        HallazgoAuditoria h = HallazgoAuditoria.reconstituir(HallazgoId.nuevo(), "T", "D", "TI", Severidad.MEDIA,
                DETECCION, EstadoHallazgo.CERRADO, PLAN, cierre);

        assertEquals(cierre, h.getFechaCierre());
    }

    @Test
    void reconstituirRechazaDatosIncoherentes() {
        assertThrows(IllegalStateException.class, () -> HallazgoAuditoria.reconstituir(HallazgoId.nuevo(), "T", "D",
                "TI", Severidad.MEDIA, DETECCION, EstadoHallazgo.EN_REMEDIACION, null, null));
        assertThrows(IllegalStateException.class, () -> HallazgoAuditoria.reconstituir(HallazgoId.nuevo(), "T", "D",
                "TI", Severidad.MEDIA, DETECCION, EstadoHallazgo.CERRADO, PLAN, null));
    }
}
