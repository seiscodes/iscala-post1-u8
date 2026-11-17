package com.example.auditoria.usecase.impl;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.CerrarHallazgoUseCase;
import com.example.auditoria.usecase.HallazgoNotFoundException;
import com.example.auditoria.usecase.port.HallazgoRepositoryPort;
import com.example.auditoria.usecase.port.HistorialAuditoriaPort;

import java.time.Clock;
import java.time.LocalDate;

/** La fecha de cierre sale de un Clock (java.time) para poder fijarla en las pruebas. */
public class CerrarHallazgoService implements CerrarHallazgoUseCase {

    private final HallazgoRepositoryPort repo;
    private final HistorialAuditoriaPort historial;
    private final Clock reloj;

    public CerrarHallazgoService(HallazgoRepositoryPort repo, HistorialAuditoriaPort historial, Clock reloj) {
        this.repo = repo;
        this.historial = historial;
        this.reloj = reloj;
    }

    @Override
    public void ejecutar(HallazgoId id, String usuario) {
        HallazgoAuditoria hallazgo = repo.buscarPorId(id)
                .orElseThrow(() -> new HallazgoNotFoundException(id));
        EstadoHallazgo anterior = hallazgo.cerrar(LocalDate.now(reloj));
        repo.guardar(hallazgo);
        historial.registrar(id, anterior, EstadoHallazgo.CERRADO, "Cierre de remediacion", usuario);
    }
}
