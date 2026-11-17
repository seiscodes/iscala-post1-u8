package com.example.auditoria.adapter.in.web;

import com.example.auditoria.domain.valueobject.TransicionInvalidaException;
import com.example.auditoria.usecase.HallazgoNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce excepciones del dominio y de los casos de uso a códigos HTTP. */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(HallazgoNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> noEncontrado(HallazgoNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    /** Transición no permitida, cierre sin plan, datos de dominio inválidos o UUID mal formado. */
    @ExceptionHandler({TransicionInvalidaException.class, IllegalStateException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> reglaDeNegocio(RuntimeException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));
        return errores;
    }

    /** JSON mal formado o un valor que no existe en el enum (por ejemplo severidad "URGENTE"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return Map.of("error", "El cuerpo de la peticion no es valido");
    }
}
