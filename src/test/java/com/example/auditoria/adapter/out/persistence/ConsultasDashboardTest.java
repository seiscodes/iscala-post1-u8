package com.example.auditoria.adapter.out.persistence;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.domain.valueobject.PlanRemediacion;
import com.example.auditoria.domain.valueobject.Severidad;
import com.example.auditoria.usecase.port.ConteoCategoria;
import com.example.auditoria.usecase.port.PromedioCategoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Las consultas del dashboard contra H2, con fechas de cierre conocidas. */
@DataJpaTest
@Import(HallazgoRepositoryAdapter.class)
class ConsultasDashboardTest {

    private static final PlanRemediacion PLAN = new PlanRemediacion("Responsable", LocalDate.of(2026, 9, 30), null);

    @Autowired
    private HallazgoRepositoryAdapter adapter;

    private void guardar(String area, Severidad severidad, LocalDate deteccion, LocalDate cierre, boolean enRemediacion) {
        HallazgoAuditoria h = new HallazgoAuditoria(HallazgoId.nuevo(), "Hallazgo " + area, null, area, severidad, deteccion);
        if (enRemediacion || cierre != null) h.iniciarRemediacion(PLAN);
        if (cierre != null) h.cerrar(cierre);
        adapter.guardar(h);
    }

    @BeforeEach
    void datos() {
        // TI: cerrados en 10 y 20 días → promedio 15
        guardar("TI", Severidad.ALTA, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 11), false);
        guardar("TI", Severidad.ALTA, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 21), false);
        // Finanzas: cerrado en 3 días, y uno abierto que no debe contar en el promedio
        guardar("Finanzas", Severidad.CRITICA, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 8), false);
        guardar("Finanzas", Severidad.BAJA, LocalDate.of(2026, 8, 5), null, false);
        // Redes: en remediación, sin cierre
        guardar("Redes", Severidad.ALTA, LocalDate.of(2026, 8, 9), null, true);
    }

    private static long total(List<ConteoCategoria> conteos, String categoria) {
        return conteos.stream().filter(c -> c.categoria().equals(categoria)).mapToLong(ConteoCategoria::total)
                .findFirst().orElse(0);
    }

    @Test
    void cuentaPorSeveridad() {
        List<ConteoCategoria> conteos = adapter.contarPorSeveridad();

        assertEquals(3, total(conteos, "ALTA"));
        assertEquals(1, total(conteos, "CRITICA"));
        assertEquals(1, total(conteos, "BAJA"));
        assertEquals(0, total(conteos, "MEDIA"));
    }

    @Test
    void cuentaPorEstado() {
        List<ConteoCategoria> conteos = adapter.contarPorEstado();

        assertEquals(3, total(conteos, "CERRADO"));
        assertEquals(1, total(conteos, "ABIERTO"));
        assertEquals(1, total(conteos, "EN_REMEDIACION"));
    }

    @Test
    void promediaLosDiasDeCierreSoloDeLosCerradosPorArea() {
        List<PromedioCategoria> promedios = adapter.promedioDiasCierrePorArea();

        assertEquals(2, promedios.size()); // Redes no tiene hallazgos cerrados
        assertEquals(15.0, promedio(promedios, "TI"), 0.001);
        assertEquals(3.0, promedio(promedios, "Finanzas"), 0.001);
    }

    private static double promedio(List<PromedioCategoria> promedios, String area) {
        return promedios.stream().filter(p -> p.categoria().equals(area)).findFirst().orElseThrow().promedioDias();
    }
}
