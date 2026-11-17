package com.example.auditoria.usecase.port;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.HallazgoId;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: lo que los casos de uso necesitan de la persistencia. */
public interface HallazgoRepositoryPort {

    void guardar(HallazgoAuditoria hallazgo);

    Optional<HallazgoAuditoria> buscarPorId(HallazgoId id);

    List<HallazgoAuditoria> buscarTodos();

    // Parte 2: consultas del dashboard en el mismo puerto, sin un repositorio de lectura aparte
    List<ConteoCategoria> contarPorSeveridad();

    List<ConteoCategoria> contarPorEstado();

    /** Solo hallazgos CERRADO: días entre fechaDeteccion y fechaCierre, promediados por área. */
    List<PromedioCategoria> promedioDiasCierrePorArea();
}
