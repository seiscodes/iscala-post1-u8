# Post-contenido — Unidad 8: Patrones Arquitectónicos II

## Descripción

Repositorio del post-contenido de la Unidad 8 de Patrones de Diseño de Software
(Sebastián Iscala Jurado, código 1152435). Sistema de seguimiento de hallazgos de
auditoría interna en un único proyecto Spring Boot (`auditoria-hallazgos`, en la raíz):

- **Parte 1:** Clean Architecture con sus cuatro círculos.
- **Parte 2:** análisis costo-beneficio de CQRS / Event Sourcing frente a dos requisitos
  nuevos (dashboard y trazabilidad legal) y la extensión que ese análisis justifica.

## Cómo ejecutar

Requisitos: JDK 17 o superior. El proyecto trae Maven Wrapper.

```bash
./mvnw clean package     # compila y ejecuta las pruebas (Windows: mvnw.cmd)
./mvnw spring-boot:run   # API en http://localhost:8080/api/hallazgos
```

Consola H2: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:auditoria_db`, usuario `sa`).

```bash
# Registrar
curl -i -X POST http://localhost:8080/api/hallazgos -H "Content-Type: application/json" \
  -d '{"titulo":"Contraseñas por defecto en servidor de pruebas","descripcion":"El servidor QA usa credenciales por defecto del fabricante","areaResponsable":"Infraestructura","severidad":"ALTA","fechaDeteccion":"2026-08-01"}'

# Iniciar remediación (con el hallazgoId devuelto)
curl -i -X PATCH http://localhost:8080/api/hallazgos/{id}/iniciar-remediacion -H "Content-Type: application/json" \
  -d '{"responsable":"Equipo de Infraestructura","fechaLimite":"2026-08-20","notas":"Rotar credenciales"}'

curl -i -X PATCH http://localhost:8080/api/hallazgos/{id}/cerrar
curl -i -X PATCH http://localhost:8080/api/hallazgos/{id}/reabrir -H "Content-Type: application/json" -d '{"motivo":"Reapareció"}'
curl -i http://localhost:8080/api/hallazgos
```

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/api/hallazgos` | 201 `{"hallazgoId": "<uuid>"}`; 400 si faltan datos |
| PATCH | `/api/hallazgos/{id}/iniciar-remediacion` | 200 `EN_REMEDIACION`; 400 si la transición no es válida |
| PATCH | `/api/hallazgos/{id}/cerrar` | 200 `CERRADO`; 400 si no hay plan o no está en remediación |
| PATCH | `/api/hallazgos/{id}/reabrir` | 200 `REABIERTO`; 400 si no estaba cerrado |
| GET | `/api/hallazgos/{id}` | 200; 404 si no existe; 400 si el id no es un UUID |
| GET | `/api/hallazgos` | 200 con todos los hallazgos |

---

## Parte 1 — Clean Architecture

### Los cuatro círculos

```
src/main/java/com/example/auditoria/
├── domain/                         ① Entities — Java puro
│   ├── entity/HallazgoAuditoria        Aggregate Root con la máquina de estados
│   └── valueobject/                    HallazgoId, Severidad, EstadoHallazgo,
│                                       PlanRemediacion, TransicionInvalidaException
├── usecase/                        ② Use Cases — Java puro (sin Spring)
│   ├── *UseCase.java                   puertos de entrada (uno por caso de uso)
│   ├── HallazgoNotFoundException
│   ├── port/HallazgoRepositoryPort     puerto de salida
│   └── impl/*Service.java              implementaciones
├── adapter/                        ③ Interface Adapters
│   ├── in/web/                         HallazgoController, ManejadorErrores, dto/
│   └── out/persistence/                HallazgoJpaEntity, HallazgoJpaRepository,
│                                       HallazgoRepositoryAdapter
├── config/AuditoriaConfiguration   ④ Frameworks & Drivers: wiring con Spring
└── AuditoriaHallazgosApplication
```

```
   ④ Spring Boot + JPA + H2  (config/, AuditoriaHallazgosApplication)
 ┌───────────────────────────────────────────────────────────┐
 │ ③ adapter/  HallazgoController      HallazgoRepositoryAdapter│
 │  ┌─────────────────────────────────────────────────────┐   │
 │  │ ② usecase/  *UseCase, *Service, HallazgoRepositoryPort│  │
 │  │  ┌───────────────────────────────────────────────┐  │   │
 │  │  │ ① domain/  HallazgoAuditoria, EstadoHallazgo… │  │   │
 │  │  └───────────────────────────────────────────────┘  │   │
 │  └─────────────────────────────────────────────────────┘   │
 └───────────────────────────────────────────────────────────┘
            las dependencias de código solo apuntan hacia adentro
```

- **Entities.** `HallazgoAuditoria` no tiene setters: el estado cambia solo con
  `iniciarRemediacion`, `cerrar` y `reabrir`, que validan la transición con
  `EstadoHallazgo.puedeTransicionarA` y devuelven el estado anterior.
- **Use Cases.** Cada caso de uso es una interfaz con una implementación sin anotaciones de
  Spring. Dependen solo de `domain/`, de `java.*` y del puerto `HallazgoRepositoryPort`.
- **Interface Adapters.** El controlador traduce HTTP y DTOs a llamadas a los casos de uso; el
  adaptador de persistencia traduce el agregado a `HallazgoJpaEntity`, la única clase con
  anotaciones JPA.
- **Frameworks & Drivers.** `AuditoriaConfiguration` crea los casos de uso con `new` y les
  pasa los adaptadores. Es el único lugar que conoce las implementaciones concretas.

Se puede comprobar la regla de dependencia con:

```bash
grep -rn "import org.springframework\|import jakarta" src/main/java/com/example/auditoria/domain src/main/java/com/example/auditoria/usecase
# (sin resultados)
```

### Cambios respecto al código de la guía

1. **`toDomain` del adaptador.** La guía reconstruía el agregado repitiendo las transiciones
   (`iniciarRemediacion`, luego `cerrar` o `reabrir`). Con un hallazgo `REABIERTO` eso falla:
   se pasa a `EN_REMEDIACION` y luego se intenta `reabrir()` desde `EN_REMEDIACION`, que la
   máquina de estados rechaza. Además, `cerrar()` ponía `LocalDate.now()` como fecha de
   cierre cada vez que se leía el hallazgo. Se agregó `HallazgoAuditoria.reconstituir(...)`,
   que carga el estado guardado tal cual y solo verifica que sea coherente.
   `HallazgoRepositoryAdapterTest` prueba la ida y vuelta en los cuatro estados.
2. **`ConsultarHallazgoUseCase` devuelve objetos de dominio**, no `HallazgoResponse`. Si el
   caso de uso devolviera el DTO web, el círculo Use Cases dependería del círculo de
   adaptadores. El controlador hace la conversión con `HallazgoResponse.desde(...)`.
3. **`cerrar(LocalDate fechaCierre)`.** La fecha la pone `CerrarHallazgoService` con un
   `Clock` (que es `java.time`, no Spring). El dominio además valida que no sea anterior a la
   fecha de detección. Así las pruebas pueden fijar la fecha de cierre.

### Pruebas

| Prueba | Círculo | Qué verifica |
|---|---|---|
| `EstadoHallazgoTest` | Entities | las 16 combinaciones origen/destino: solo 4 son válidas |
| `HallazgoAuditoriaTest` | Entities | ciclo completo, cierre sin plan, reabrir abierto, reconstitución (sin Spring) |
| `CasosDeUsoTest` | Use Cases | casos de uso con un repositorio en memoria y un `Clock` fijo (sin Spring) |
| `HallazgoRepositoryAdapterTest` | Adapters | `@DataJpaTest`: guardar y leer en cada estado |
| `HallazgoApiTest` | Todos | MockMvc: 201 con UUID, 200 remediación, 400 cerrar abierto, 200 reabrir cerrado, 404 |

---

## Decisiones de diseño

### 1. `Severidad` como enum simple y `EstadoHallazgo` como enum con máquina de estados

El criterio fue si el valor tiene reglas propias. `EstadoHallazgo` sí: no todo destino es
válido desde cada estado y esa regla pertenece al estado mismo, así que el enum la contiene
(`puedeTransicionarA`). Si la regla estuviera en el servicio o en el agregado como un
`switch`, cada lugar que cambie el estado tendría que repetirla.

`Severidad` es una clasificación: ningún valor es más válido que otro ni hay transiciones
entre ellos. Darle comportamiento (por ejemplo, un método `esUrgente()` o un plazo máximo de
remediación por severidad) sería inventar reglas que hoy nadie pidió. Se habría preferido lo
contrario si el comité definiera, por ejemplo, que un hallazgo `CRITICA` debe tener fecha
límite a máximo 15 días; en ese caso esa regla iría en `Severidad` y `PlanRemediacion` la
consultaría.

### 2. `PlanRemediacion` como Value Object embebido y no como agregado separado

El criterio es el límite de consistencia transaccional (sección 3.3 de la guía): lo que debe
ser consistente en la misma transacción va en el mismo agregado. Las invariantes "no hay
`EN_REMEDIACION` sin plan" y "no se cierra sin plan" relacionan el estado del hallazgo con
la existencia del plan. Si el plan fuera un agregado aparte con su propio repositorio, habría
un momento en que el hallazgo dice `EN_REMEDIACION` y el plan todavía no existe (o se borró),
y la regla tendría que verificarse con consultas entre dos agregados.

Además, el plan no tiene identidad propia ni ciclo de vida independiente: no se consulta
solo, no cambia sin que cambie el hallazgo y, si se reemplaza, el anterior deja de importar.
Eso lo describe un Value Object inmutable. En la base de datos queda en tres columnas de la
misma tabla (`plan_responsable`, `plan_fecha_limite`, `plan_notas`).
