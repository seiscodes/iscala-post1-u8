package com.example.auditoria.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReabrirRequest(@NotBlank(message = "El motivo es obligatorio") String motivo) {
}
