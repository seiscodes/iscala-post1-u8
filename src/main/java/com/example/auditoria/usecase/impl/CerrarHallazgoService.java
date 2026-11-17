package com.example.auditoria.usecase.impl;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.CerrarHallazgoUseCase;
import com.example.auditoria.usecase.HallazgoNotFoundException;
import com.example.auditoria.usecase.port.HallazgoRepositoryPort;

import java.time.Clock;
import java.time.LocalDate;

/** La fecha de cierre sale de un Clock (java.time) para poder fijarla en las pruebas. */
public class CerrarHallazgoService implements CerrarHallazgoUseCase {

    private final HallazgoRepositoryPort repo;
    private final Clock reloj;

    public CerrarHallazgoService(HallazgoRepositoryPort repo, Clock reloj) {
        this.repo = repo;
        this.reloj = reloj;
    }

    @Override
    public void ejecutar(HallazgoId id) {
        HallazgoAuditoria hallazgo = repo.buscarPorId(id)
                .orElseThrow(() -> new HallazgoNotFoundException(id));
        hallazgo.cerrar(LocalDate.now(reloj));
        repo.guardar(hallazgo);
    }
}
