package com.example.auditoria.usecase;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.HallazgoId;

import java.util.List;

/**
 * Devuelve objetos de dominio. El controlador los convierte a su DTO; si el caso de uso
 * devolviera HallazgoResponse dependería del círculo de adaptadores.
 */
public interface ConsultarHallazgoUseCase {

    HallazgoAuditoria buscarPorId(HallazgoId id);

    List<HallazgoAuditoria> listarTodos();
}
