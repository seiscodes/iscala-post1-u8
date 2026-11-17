package com.example.auditoria.usecase;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.port.CambioEstadoView;
import com.example.auditoria.usecase.port.HistorialAuditoriaPort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Fake de la bitácora para las pruebas de casos de uso. */
public class HistorialEnMemoria implements HistorialAuditoriaPort {

    private record Registro(HallazgoId id, CambioEstadoView cambio) {
    }

    private final List<Registro> registros = new ArrayList<>();

    @Override
    public void registrar(HallazgoId hallazgoId, EstadoHallazgo anterior, EstadoHallazgo nuevo,
                          String motivo, String usuario) {
        registros.add(new Registro(hallazgoId, new CambioEstadoView(
                anterior == null ? null : anterior.name(), nuevo.name(), motivo, usuario, LocalDateTime.now())));
    }

    @Override
    public List<CambioEstadoView> listarPorHallazgo(HallazgoId hallazgoId) {
        return registros.stream().filter(r -> r.id().equals(hallazgoId)).map(Registro::cambio).toList();
    }

    public int total() {
        return registros.size();
    }
}
