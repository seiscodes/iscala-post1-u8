package com.example.auditoria.domain.entity;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.domain.valueobject.PlanRemediacion;
import com.example.auditoria.domain.valueobject.Severidad;
import com.example.auditoria.domain.valueobject.TransicionInvalidaException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate Root. El estado solo cambia mediante los métodos de dominio, que validan la
 * máquina de estados de EstadoHallazgo. El plan de remediación vive dentro del agregado
 * porque las reglas "no hay remediación sin plan" y "no se cierra sin plan" deben
 * cumplirse en la misma transacción.
 */
public class HallazgoAuditoria {

    private final HallazgoId id;
    private final String titulo;
    private final String descripcion;
    private final String areaResponsable;
    private final Severidad severidad;
    private final LocalDate fechaDeteccion;
    private EstadoHallazgo estado;
    private PlanRemediacion planRemediacion;
    private LocalDate fechaCierre;

    public HallazgoAuditoria(HallazgoId id, String titulo, String descripcion,
                             String areaResponsable, Severidad severidad, LocalDate fechaDeteccion) {
        Objects.requireNonNull(id, "El id es obligatorio");
        if (titulo == null || titulo.isBlank())
            throw new IllegalArgumentException("El titulo es obligatorio");
        if (areaResponsable == null || areaResponsable.isBlank())
            throw new IllegalArgumentException("El area responsable es obligatoria");
        if (severidad == null)
            throw new IllegalArgumentException("La severidad es obligatoria");
        if (fechaDeteccion == null)
            throw new IllegalArgumentException("La fecha de deteccion es obligatoria");
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.areaResponsable = areaResponsable;
        this.severidad = severidad;
        this.fechaDeteccion = fechaDeteccion;
        this.estado = EstadoHallazgo.ABIERTO;
    }

    /**
     * Reconstruye un hallazgo ya guardado con su estado tal cual está en la base de datos.
     * No vuelve a recorrer las transiciones (eso fallaría con REABIERTO y cambiaría la
     * fecha de cierre); solo comprueba que los datos sean coherentes entre sí.
     */
    public static HallazgoAuditoria reconstituir(HallazgoId id, String titulo, String descripcion,
                                                 String areaResponsable, Severidad severidad,
                                                 LocalDate fechaDeteccion, EstadoHallazgo estado,
                                                 PlanRemediacion plan, LocalDate fechaCierre) {
        HallazgoAuditoria h = new HallazgoAuditoria(id, titulo, descripcion, areaResponsable, severidad, fechaDeteccion);
        Objects.requireNonNull(estado, "El estado es obligatorio");
        boolean requierePlan = estado == EstadoHallazgo.EN_REMEDIACION || estado == EstadoHallazgo.CERRADO;
        if (requierePlan && plan == null)
            throw new IllegalStateException("Hallazgo " + id + " en estado " + estado + " sin plan de remediacion");
        if ((estado == EstadoHallazgo.CERRADO) != (fechaCierre != null))
            throw new IllegalStateException("Hallazgo " + id + ": la fecha de cierre no corresponde al estado " + estado);
        h.estado = estado;
        h.planRemediacion = plan;
        h.fechaCierre = fechaCierre;
        return h;
    }

    /** ABIERTO o REABIERTO → EN_REMEDIACION. Un plan nuevo reemplaza al anterior. */
    public EstadoHallazgo iniciarRemediacion(PlanRemediacion plan) {
        Objects.requireNonNull(plan, "El plan de remediacion es obligatorio");
        EstadoHallazgo anterior = transicionar(EstadoHallazgo.EN_REMEDIACION);
        this.planRemediacion = plan;
        return anterior;
    }

    /** EN_REMEDIACION → CERRADO. */
    public EstadoHallazgo cerrar(LocalDate fechaCierre) {
        Objects.requireNonNull(fechaCierre, "La fecha de cierre es obligatoria");
        if (planRemediacion == null)
            throw new IllegalStateException("No se puede cerrar un hallazgo sin plan de remediacion");
        if (fechaCierre.isBefore(fechaDeteccion))
            throw new IllegalArgumentException("La fecha de cierre no puede ser anterior a la de deteccion");
        EstadoHallazgo anterior = transicionar(EstadoHallazgo.CERRADO);
        this.fechaCierre = fechaCierre;
        return anterior;
    }

    /** CERRADO → REABIERTO. */
    public EstadoHallazgo reabrir() {
        EstadoHallazgo anterior = transicionar(EstadoHallazgo.REABIERTO);
        this.fechaCierre = null;
        return anterior;
    }

    private EstadoHallazgo transicionar(EstadoHallazgo destino) {
        if (!estado.puedeTransicionarA(destino))
            throw new TransicionInvalidaException(estado, destino);
        EstadoHallazgo anterior = this.estado;
        this.estado = destino;
        return anterior;
    }

    public HallazgoId getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public String getAreaResponsable() { return areaResponsable; }
    public Severidad getSeveridad() { return severidad; }
    public LocalDate getFechaDeteccion() { return fechaDeteccion; }
    public EstadoHallazgo getEstado() { return estado; }
    public PlanRemediacion getPlanRemediacion() { return planRemediacion; }
    public LocalDate getFechaCierre() { return fechaCierre; }
}
