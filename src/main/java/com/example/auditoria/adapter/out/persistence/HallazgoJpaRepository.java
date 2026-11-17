package com.example.auditoria.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HallazgoJpaRepository extends JpaRepository<HallazgoJpaEntity, String> {

    @Query("SELECT h.severidad AS categoria, COUNT(h) AS total FROM HallazgoJpaEntity h "
            + "GROUP BY h.severidad ORDER BY h.severidad")
    List<ConteoProjection> contarPorSeveridad();

    @Query("SELECT h.estado AS categoria, COUNT(h) AS total FROM HallazgoJpaEntity h "
            + "GROUP BY h.estado ORDER BY h.estado")
    List<ConteoProjection> contarPorEstado();

    /**
     * Consulta nativa porque DATEDIFF es una función de H2 y no forma parte de JPQL.
     * Si se cambia de motor, esta es la única consulta que hay que adaptar.
     */
    @Query(value = "SELECT area_responsable AS categoria, "
            + "AVG(CAST(DATEDIFF('DAY', fecha_deteccion, fecha_cierre) AS DOUBLE PRECISION)) AS promedio "
            + "FROM hallazgos WHERE estado = 'CERRADO' GROUP BY area_responsable ORDER BY area_responsable",
            nativeQuery = true)
    List<PromedioProjection> promedioDiasCierrePorArea();

    /** El tipo de la categoría depende de la consulta (enum o texto); se convierte con toString. */
    interface ConteoProjection {
        Object getCategoria();

        Long getTotal();
    }

    /** H2 devuelve el AVG como BigDecimal; se recibe como Number para no depender de eso. */
    interface PromedioProjection {
        String getCategoria();

        Number getPromedio();
    }
}
