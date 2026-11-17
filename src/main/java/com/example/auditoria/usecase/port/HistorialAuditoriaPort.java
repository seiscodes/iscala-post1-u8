package com.example.auditoria.usecase.port;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;

import java.util.List;

/**
 * Bitácora de cambios de estado. Solo permite agregar y leer: no hay operación para
 * modificar o borrar un registro.
 */
public interface HistorialAuditoriaPort {

    /** @param anterior null cuando el cambio es el registro inicial del hallazgo */
    void registrar(HallazgoId hallazgoId, EstadoHallazgo anterior, EstadoHallazgo nuevo,
                   String motivo, String usuario);

    List<CambioEstadoView> listarPorHallazgo(HallazgoId hallazgoId);
}
