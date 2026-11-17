package com.example.auditoria.adapter.in.web;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.ConsultarHallazgoUseCase;
import com.example.auditoria.usecase.IniciarRemediacionUseCase;
import com.example.auditoria.usecase.RegistrarHallazgoUseCase;
import com.example.auditoria.domain.valueobject.Severidad;
import com.example.auditoria.usecase.port.HistorialAuditoriaPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * Sin @Transactional en la prueba, para que cada caso de uso abra su propia transacción.
 * Si falla la escritura en la bitácora, el cambio de estado también se revierte.
 * Usa su propia base en memoria (@AutoConfigureTestDatabase) porque sus datos sí se confirman.
 */
@SpringBootTest
@AutoConfigureTestDatabase
class TransaccionBitacoraTest {

    @Autowired
    private RegistrarHallazgoUseCase registrar;

    @Autowired
    private IniciarRemediacionUseCase iniciar;

    @Autowired
    private ConsultarHallazgoUseCase consultar;

    @SpyBean
    private HistorialAuditoriaPort historial;

    @Test
    void siFallaLaBitacoraElHallazgoNoCambiaDeEstado() {
        HallazgoId id = registrar.ejecutar("Hallazgo", null, "TI", Severidad.ALTA, LocalDate.of(2026, 8, 1), "auditor");
        doThrow(new IllegalStateException("bitacora no disponible"))
                .when(historial).registrar(eq(id), any(), eq(EstadoHallazgo.EN_REMEDIACION), anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> iniciar.ejecutar(id, "Responsable", LocalDate.of(2026, 8, 20), null, "jefe"));

        assertEquals(EstadoHallazgo.ABIERTO, consultar.buscarPorId(id).getEstado());
    }
}
