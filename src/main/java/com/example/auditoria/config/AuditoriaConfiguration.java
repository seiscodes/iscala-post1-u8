package com.example.auditoria.config;

import com.example.auditoria.usecase.CerrarHallazgoUseCase;
import com.example.auditoria.usecase.ConsultarHallazgoUseCase;
import com.example.auditoria.usecase.ConsultarHistorialUseCase;
import com.example.auditoria.usecase.IniciarRemediacionUseCase;
import com.example.auditoria.usecase.ReabrirHallazgoUseCase;
import com.example.auditoria.usecase.RegistrarHallazgoUseCase;
import com.example.auditoria.usecase.impl.CerrarHallazgoService;
import com.example.auditoria.usecase.impl.ConsultarHallazgoService;
import com.example.auditoria.usecase.impl.ConsultarHistorialService;
import com.example.auditoria.usecase.impl.IniciarRemediacionService;
import com.example.auditoria.usecase.impl.ReabrirHallazgoService;
import com.example.auditoria.usecase.impl.RegistrarHallazgoService;
import com.example.auditoria.usecase.port.HallazgoRepositoryPort;
import com.example.auditoria.usecase.port.HistorialAuditoriaPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Círculo Frameworks & Drivers: conecta los casos de uso (clases sin Spring) con los
 * adaptadores. Es el único lugar que conoce las implementaciones concretas.
 *
 * Las transacciones también se definen aquí: el círculo Use Cases no puede usar
 * @Transactional, así que cada caso de uso que escribe se envuelve en una lambda que lo
 * ejecuta dentro de un TransactionTemplate. Así el cambio de estado y su registro en la
 * bitácora se guardan juntos o no se guarda ninguno.
 */
@Configuration
public class AuditoriaConfiguration {

    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public RegistrarHallazgoUseCase registrarHallazgoUseCase(HallazgoRepositoryPort repo,
                                                             HistorialAuditoriaPort historial,
                                                             TransactionTemplate tx) {
        RegistrarHallazgoService servicio = new RegistrarHallazgoService(repo, historial);
        return (titulo, descripcion, area, severidad, fecha, usuario) ->
                tx.execute(estado -> servicio.ejecutar(titulo, descripcion, area, severidad, fecha, usuario));
    }

    @Bean
    public IniciarRemediacionUseCase iniciarRemediacionUseCase(HallazgoRepositoryPort repo,
                                                               HistorialAuditoriaPort historial,
                                                               TransactionTemplate tx) {
        IniciarRemediacionService servicio = new IniciarRemediacionService(repo, historial);
        return (id, responsable, fechaLimite, notas, usuario) ->
                tx.executeWithoutResult(estado -> servicio.ejecutar(id, responsable, fechaLimite, notas, usuario));
    }

    @Bean
    public CerrarHallazgoUseCase cerrarHallazgoUseCase(HallazgoRepositoryPort repo,
                                                       HistorialAuditoriaPort historial,
                                                       Clock reloj, TransactionTemplate tx) {
        CerrarHallazgoService servicio = new CerrarHallazgoService(repo, historial, reloj);
        return (id, usuario) -> tx.executeWithoutResult(estado -> servicio.ejecutar(id, usuario));
    }

    @Bean
    public ReabrirHallazgoUseCase reabrirHallazgoUseCase(HallazgoRepositoryPort repo,
                                                         HistorialAuditoriaPort historial,
                                                         TransactionTemplate tx) {
        ReabrirHallazgoService servicio = new ReabrirHallazgoService(repo, historial);
        return (id, motivo, usuario) -> tx.executeWithoutResult(estado -> servicio.ejecutar(id, motivo, usuario));
    }

    @Bean
    public ConsultarHallazgoUseCase consultarHallazgoUseCase(HallazgoRepositoryPort repo) {
        return new ConsultarHallazgoService(repo);
    }

    @Bean
    public ConsultarHistorialUseCase consultarHistorialUseCase(HallazgoRepositoryPort repo,
                                                               HistorialAuditoriaPort historial) {
        return new ConsultarHistorialService(repo, historial);
    }
}
