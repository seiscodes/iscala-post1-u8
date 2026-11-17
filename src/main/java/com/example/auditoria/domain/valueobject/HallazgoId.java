package com.example.auditoria.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Identidad tipada del hallazgo: evita confundirla con cualquier otro UUID o String. */
public record HallazgoId(UUID valor) {

    public HallazgoId {
        Objects.requireNonNull(valor, "HallazgoId no puede ser nulo");
    }

    public static HallazgoId nuevo() {
        return new HallazgoId(UUID.randomUUID());
    }

    /** Lanza IllegalArgumentException si el texto no es un UUID válido. */
    public static HallazgoId de(String texto) {
        return new HallazgoId(UUID.fromString(texto));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
