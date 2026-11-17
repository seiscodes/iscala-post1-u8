package com.example.auditoria.adapter.out.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Extiende Repository (no JpaRepository) a propósito: solo expone insertar y consultar.
 * No existen deleteById, deleteAll ni saveAll que pudieran usarse para alterar la bitácora.
 */
public interface HistorialCambioEstadoJpaRepository extends Repository<HistorialCambioEstadoJpaEntity, Long> {

    HistorialCambioEstadoJpaEntity save(HistorialCambioEstadoJpaEntity registro);

    /** Orden cronológico; el id desempata dos cambios registrados en el mismo instante. */
    List<HistorialCambioEstadoJpaEntity> findByHallazgoIdOrderByFechaAscIdAsc(String hallazgoId);
}
