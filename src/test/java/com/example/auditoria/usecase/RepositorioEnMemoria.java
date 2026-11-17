package com.example.auditoria.usecase;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.port.HallazgoRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake del puerto: guarda en un mapa. Permite probar los casos de uso sin base de datos. */
public class RepositorioEnMemoria implements HallazgoRepositoryPort {

    private final Map<HallazgoId, HallazgoAuditoria> datos = new LinkedHashMap<>();
    private int guardados = 0;

    @Override
    public void guardar(HallazgoAuditoria hallazgo) {
        datos.put(hallazgo.getId(), hallazgo);
        guardados++;
    }

    @Override
    public Optional<HallazgoAuditoria> buscarPorId(HallazgoId id) {
        return Optional.ofNullable(datos.get(id));
    }

    @Override
    public List<HallazgoAuditoria> buscarTodos() {
        return new ArrayList<>(datos.values());
    }

    public int vecesGuardado() {
        return guardados;
    }
}
