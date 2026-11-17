package com.example.auditoria.domain;

import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.auditoria.domain.valueobject.EstadoHallazgo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadoHallazgoTest {

    /** Única transición válida desde cada estado, según el diagrama de la guía. */
    private static final Map<EstadoHallazgo, EstadoHallazgo> VALIDAS = Map.of(
            ABIERTO, EN_REMEDIACION,
            EN_REMEDIACION, CERRADO,
            CERRADO, REABIERTO,
            REABIERTO, EN_REMEDIACION);

    @Test
    void soloSePermiteUnaTransicionDesdeCadaEstado() {
        for (EstadoHallazgo origen : EstadoHallazgo.values()) {
            Set<EstadoHallazgo> permitidos = EnumSet.noneOf(EstadoHallazgo.class);
            for (EstadoHallazgo destino : EstadoHallazgo.values()) {
                if (origen.puedeTransicionarA(destino)) permitidos.add(destino);
            }
            assertEquals(Set.of(VALIDAS.get(origen)), permitidos, "desde " + origen);
        }
    }
}
