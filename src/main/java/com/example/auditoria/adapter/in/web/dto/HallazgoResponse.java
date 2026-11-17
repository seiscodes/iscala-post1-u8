package com.example.auditoria.adapter.in.web.dto;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.PlanRemediacion;

import java.time.LocalDate;

public record HallazgoResponse(
        String id,
        String titulo,
        String descripcion,
        String areaResponsable,
        String severidad,
        String estado,
        LocalDate fechaDeteccion,
        LocalDate fechaCierre,
        PlanResponse plan
) {
    public record PlanResponse(String responsable, LocalDate fechaLimite, String notas) {
    }

    public static HallazgoResponse desde(HallazgoAuditoria h) {
        PlanRemediacion p = h.getPlanRemediacion();
        return new HallazgoResponse(h.getId().toString(), h.getTitulo(), h.getDescripcion(),
                h.getAreaResponsable(), h.getSeveridad().name(), h.getEstado().name(),
                h.getFechaDeteccion(), h.getFechaCierre(),
                p == null ? null : new PlanResponse(p.responsable(), p.fechaLimite(), p.notas()));
    }
}
