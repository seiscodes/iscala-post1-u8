package com.example.auditoria.usecase;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.domain.valueobject.Severidad;
import com.example.auditoria.domain.valueobject.TransicionInvalidaException;
import com.example.auditoria.usecase.impl.CerrarHallazgoService;
import com.example.auditoria.usecase.impl.ConsultarHallazgoService;
import com.example.auditoria.usecase.impl.ConsultarHistorialService;
import com.example.auditoria.usecase.port.CambioEstadoView;
import com.example.auditoria.usecase.impl.IniciarRemediacionService;
import com.example.auditoria.usecase.impl.ReabrirHallazgoService;
import com.example.auditoria.usecase.impl.RegistrarHallazgoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Los casos de uso no dependen de Spring: se arman a mano con el repositorio en memoria. */
class CasosDeUsoTest {

    private static final Clock RELOJ_18_AGOSTO =
            Clock.fixed(Instant.parse("2026-08-18T15:00:00Z"), ZoneId.of("America/Bogota"));

    private RepositorioEnMemoria repo;
    private HistorialEnMemoria historial;
    private RegistrarHallazgoService registrar;
    private IniciarRemediacionService iniciar;
    private CerrarHallazgoService cerrar;
    private ReabrirHallazgoService reabrir;
    private ConsultarHallazgoService consultar;

    @BeforeEach
    void setUp() {
        repo = new RepositorioEnMemoria();
        historial = new HistorialEnMemoria();
        registrar = new RegistrarHallazgoService(repo, historial);
        iniciar = new IniciarRemediacionService(repo, historial);
        cerrar = new CerrarHallazgoService(repo, historial, RELOJ_18_AGOSTO);
        reabrir = new ReabrirHallazgoService(repo, historial);
        consultar = new ConsultarHallazgoService(repo);
    }

    private HallazgoId registrarUno() {
        return registrar.ejecutar("Contraseñas por defecto", "QA con credenciales del fabricante",
                "Infraestructura", Severidad.ALTA, LocalDate.of(2026, 8, 1), "auditor.lopez");
    }

    @Test
    void registrarGuardaUnHallazgoAbierto() {
        HallazgoId id = registrarUno();

        assertEquals(EstadoHallazgo.ABIERTO, consultar.buscarPorId(id).getEstado());
        assertEquals(1, repo.vecesGuardado());
    }

    @Test
    void cicloCompletoConFechaDeCierreDelReloj() {
        HallazgoId id = registrarUno();

        iniciar.ejecutar(id, "Equipo de Infraestructura", LocalDate.of(2026, 8, 20), "Rotar credenciales", "jefe.infra");
        cerrar.ejecutar(id, "jefe.infra");

        assertEquals(EstadoHallazgo.CERRADO, consultar.buscarPorId(id).getEstado());
        assertEquals(LocalDate.of(2026, 8, 18), consultar.buscarPorId(id).getFechaCierre());

        reabrir.ejecutar(id, "La credencial volvió a aparecer en el escaneo", "auditor.lopez");
        assertEquals(EstadoHallazgo.REABIERTO, consultar.buscarPorId(id).getEstado());
    }

    @Test
    void cerrarSinRemediacionFallaYNoGuarda() {
        HallazgoId id = registrarUno();

        assertThrows(IllegalStateException.class, () -> cerrar.ejecutar(id, "jefe.infra"));
        assertEquals(1, repo.vecesGuardado());
        assertEquals(1, historial.total()); // solo el registro inicial
    }

    @Test
    void reabrirUnHallazgoAbiertoEsTransicionInvalida() {
        HallazgoId id = registrarUno();

        assertThrows(TransicionInvalidaException.class, () -> reabrir.ejecutar(id, "motivo", "auditor.lopez"));
    }

    @Test
    void reabrirExigeMotivo() {
        HallazgoId id = registrarUno();

        assertThrows(IllegalArgumentException.class, () -> reabrir.ejecutar(id, " ", "auditor.lopez"));
    }

    @Test
    void unIdInexistenteLanzaNotFound() {
        HallazgoId otro = HallazgoId.nuevo();

        assertThrows(HallazgoNotFoundException.class, () -> iniciar.ejecutar(otro, "x", LocalDate.now(), null, "u"));
        assertThrows(HallazgoNotFoundException.class, () -> consultar.buscarPorId(otro));
    }

    // ---------- Parte 2: bitácora ----------

    @Test
    void cadaTransicionExitosaAgregaUnRegistroConUsuarioYEstados() {
        HallazgoId id = registrarUno();
        iniciar.ejecutar(id, "Equipo de Infraestructura", LocalDate.of(2026, 8, 20), null, "jefe.infra");
        cerrar.ejecutar(id, "jefe.infra");
        reabrir.ejecutar(id, "Reapareció en el escaneo de octubre", "auditor.lopez");

        List<CambioEstadoView> cambios = new ConsultarHistorialService(repo, historial).ejecutar(id);

        assertEquals(4, cambios.size());
        assertNull(cambios.get(0).estadoAnterior());
        assertEquals("ABIERTO", cambios.get(0).estadoNuevo());
        assertEquals("auditor.lopez", cambios.get(0).usuario());
        assertEquals("ABIERTO", cambios.get(1).estadoAnterior());
        assertEquals("EN_REMEDIACION", cambios.get(1).estadoNuevo());
        assertEquals("CERRADO", cambios.get(2).estadoNuevo());
        assertEquals("CERRADO", cambios.get(3).estadoAnterior());
        assertEquals("REABIERTO", cambios.get(3).estadoNuevo());
        assertEquals("Reapareció en el escaneo de octubre", cambios.get(3).motivo());
    }

    @Test
    void unaTransicionRechazadaNoDejaRegistro() {
        HallazgoId id = registrarUno();

        assertThrows(TransicionInvalidaException.class, () -> reabrir.ejecutar(id, "x", "u"));

        assertEquals(1, historial.total());
    }

    @Test
    void elHistorialDeUnHallazgoInexistenteEs404() {
        assertThrows(HallazgoNotFoundException.class,
                () -> new ConsultarHistorialService(repo, historial).ejecutar(HallazgoId.nuevo()));
    }
}
