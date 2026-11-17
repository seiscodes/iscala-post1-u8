package com.example.auditoria.usecase;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.domain.valueobject.Severidad;
import com.example.auditoria.domain.valueobject.TransicionInvalidaException;
import com.example.auditoria.usecase.impl.CerrarHallazgoService;
import com.example.auditoria.usecase.impl.ConsultarHallazgoService;
import com.example.auditoria.usecase.impl.IniciarRemediacionService;
import com.example.auditoria.usecase.impl.ReabrirHallazgoService;
import com.example.auditoria.usecase.impl.RegistrarHallazgoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** Los casos de uso no dependen de Spring: se arman a mano con el repositorio en memoria. */
class CasosDeUsoTest {

    private static final Clock RELOJ_18_AGOSTO =
            Clock.fixed(Instant.parse("2026-08-18T15:00:00Z"), ZoneId.of("America/Bogota"));

    private RepositorioEnMemoria repo;
    private RegistrarHallazgoService registrar;
    private IniciarRemediacionService iniciar;
    private CerrarHallazgoService cerrar;
    private ReabrirHallazgoService reabrir;
    private ConsultarHallazgoService consultar;

    @BeforeEach
    void setUp() {
        repo = new RepositorioEnMemoria();
        registrar = new RegistrarHallazgoService(repo);
        iniciar = new IniciarRemediacionService(repo);
        cerrar = new CerrarHallazgoService(repo, RELOJ_18_AGOSTO);
        reabrir = new ReabrirHallazgoService(repo);
        consultar = new ConsultarHallazgoService(repo);
    }

    private HallazgoId registrarUno() {
        return registrar.ejecutar("Contraseñas por defecto", "QA con credenciales del fabricante",
                "Infraestructura", Severidad.ALTA, LocalDate.of(2026, 8, 1));
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

        iniciar.ejecutar(id, "Equipo de Infraestructura", LocalDate.of(2026, 8, 20), "Rotar credenciales");
        cerrar.ejecutar(id);

        assertEquals(EstadoHallazgo.CERRADO, consultar.buscarPorId(id).getEstado());
        assertEquals(LocalDate.of(2026, 8, 18), consultar.buscarPorId(id).getFechaCierre());

        reabrir.ejecutar(id, "La credencial volvió a aparecer en el escaneo");
        assertEquals(EstadoHallazgo.REABIERTO, consultar.buscarPorId(id).getEstado());
    }

    @Test
    void cerrarSinRemediacionFallaYNoGuarda() {
        HallazgoId id = registrarUno();

        assertThrows(IllegalStateException.class, () -> cerrar.ejecutar(id));
        assertEquals(1, repo.vecesGuardado());
    }

    @Test
    void reabrirUnHallazgoAbiertoEsTransicionInvalida() {
        HallazgoId id = registrarUno();

        assertThrows(TransicionInvalidaException.class, () -> reabrir.ejecutar(id, "motivo"));
    }

    @Test
    void reabrirExigeMotivo() {
        HallazgoId id = registrarUno();

        assertThrows(IllegalArgumentException.class, () -> reabrir.ejecutar(id, " "));
    }

    @Test
    void unIdInexistenteLanzaNotFound() {
        HallazgoId otro = HallazgoId.nuevo();

        assertThrows(HallazgoNotFoundException.class, () -> iniciar.ejecutar(otro, "x", LocalDate.now(), null));
        assertThrows(HallazgoNotFoundException.class, () -> consultar.buscarPorId(otro));
    }
}
