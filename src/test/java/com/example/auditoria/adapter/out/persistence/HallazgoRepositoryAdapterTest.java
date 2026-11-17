package com.example.auditoria.adapter.out.persistence;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.domain.valueobject.PlanRemediacion;
import com.example.auditoria.domain.valueobject.Severidad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Ida y vuelta agregado → tabla → agregado con H2, en cada estado posible. */
@DataJpaTest
@Import(HallazgoRepositoryAdapter.class)
class HallazgoRepositoryAdapterTest {

    private static final PlanRemediacion PLAN =
            new PlanRemediacion("Equipo de Redes", LocalDate.of(2026, 9, 15), "Cerrar puertos");

    @Autowired
    private HallazgoRepositoryAdapter adapter;

    private HallazgoAuditoria nuevo() {
        return new HallazgoAuditoria(HallazgoId.nuevo(), "Puertos abiertos en firewall", "Puertos 21 y 23",
                "Redes", Severidad.CRITICA, LocalDate.of(2026, 9, 1));
    }

    private HallazgoAuditoria guardarYLeer(HallazgoAuditoria h) {
        adapter.guardar(h);
        return adapter.buscarPorId(h.getId()).orElseThrow();
    }

    @Test
    void conservaUnHallazgoAbierto() {
        HallazgoAuditoria leido = guardarYLeer(nuevo());

        assertEquals(EstadoHallazgo.ABIERTO, leido.getEstado());
        assertEquals("Puertos abiertos en firewall", leido.getTitulo());
        assertEquals(Severidad.CRITICA, leido.getSeveridad());
        assertNull(leido.getPlanRemediacion());
    }

    @Test
    void conservaElPlanDeUnHallazgoEnRemediacion() {
        HallazgoAuditoria h = nuevo();
        h.iniciarRemediacion(PLAN);

        HallazgoAuditoria leido = guardarYLeer(h);

        assertEquals(EstadoHallazgo.EN_REMEDIACION, leido.getEstado());
        assertEquals(PLAN, leido.getPlanRemediacion());
    }

    @Test
    void conservaLaFechaDeCierreRealNoLaDeHoy() {
        HallazgoAuditoria h = nuevo();
        h.iniciarRemediacion(PLAN);
        h.cerrar(LocalDate.of(2026, 9, 10));

        HallazgoAuditoria leido = guardarYLeer(h);

        assertEquals(EstadoHallazgo.CERRADO, leido.getEstado());
        assertEquals(LocalDate.of(2026, 9, 10), leido.getFechaCierre());
    }

    @Test
    void puedeLeerUnHallazgoReabierto() {
        HallazgoAuditoria h = nuevo();
        h.iniciarRemediacion(PLAN);
        h.cerrar(LocalDate.of(2026, 9, 10));
        h.reabrir();

        HallazgoAuditoria leido = guardarYLeer(h);

        assertEquals(EstadoHallazgo.REABIERTO, leido.getEstado());
        assertNull(leido.getFechaCierre());
    }

    @Test
    void guardarDeNuevoActualizaLaMismaFila() {
        HallazgoAuditoria h = nuevo();
        adapter.guardar(h);
        h.iniciarRemediacion(PLAN);
        adapter.guardar(h);

        assertEquals(1, adapter.buscarTodos().size());
        assertEquals(EstadoHallazgo.EN_REMEDIACION, adapter.buscarPorId(h.getId()).orElseThrow().getEstado());
    }
}
