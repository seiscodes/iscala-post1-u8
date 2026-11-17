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

## Parte 2 — Análisis costo-beneficio de CQRS / Event Sourcing

Requisitos nuevos del comité de auditoría:

1. **Dashboard:** hallazgos por severidad, hallazgos por estado y promedio de días entre
   detección y cierre de los hallazgos cerrados, por área responsable.
2. **Trazabilidad legal:** reconstruir cronológicamente cada cambio de estado de un hallazgo
   (quién, cuándo, de qué estado a qué estado) sin que el registro se pueda alterar después.

Este análisis se escribió antes de implementar, aplicando los criterios de las secciones 4.4
y 7 de la guía.

**Escala y carga.** El sistema lo usa el área de auditoría interna: unas pocas personas que
registran hallazgos y un comité que consulta el dashboard una vez al mes antes de su reunión.
Siendo generosos, decenas de escrituras al día y algunas decenas de consultas al mes. No hay
diferencia de escala entre lecturas y escrituras que obligue a escalar una de las dos por
separado; las dos caben con mucha holgura en la misma base de datos.

**Complejidad de las consultas.** Los tres indicadores son un `COUNT ... GROUP BY severidad`,
un `COUNT ... GROUP BY estado` y un `AVG(dias) ... WHERE estado = 'CERRADO' GROUP BY area`
sobre una sola tabla. No hay joins entre agregados, ni búsqueda de texto, ni datos que
tengan una forma distinta a la del modelo de escritura. Se resuelven con consultas sobre el
mismo esquema; un modelo de lectura con otra tecnología (otra base, Elasticsearch, tablas
desnormalizadas) no agregaría nada.

**Consistencia.** El comité revisa el dashboard bajo demanda, antes de cada reunión. Lo
esperable es que refleje el estado en el momento de la consulta, como cualquier reporte. Con
una consulta directa la consistencia es inmediata y gratis; con CQRS habría que mantener una
proyección y aceptar (o resolver) un desfase que hoy no existe.

**Naturaleza de la trazabilidad.** Cumplimiento pide ver la secuencia de cambios, no
reconstruir el estado del hallazgo reproduciendo eventos. El estado actual ya está guardado y
es correcto. Lo que falta es una bitácora cronológica, de solo inserción, escrita en la misma
transacción que cada cambio. Event Sourcing convertiría esa bitácora en la fuente de verdad
del estado, que es un cambio de fondo que el requisito no pide.

**Señales de sobre-ingeniería (sección 7.2).** Las tres aparecen: no hay un experto de negocio
disponible para modelar eventos (el modelo de estados lo definió la guía), el equipo es una
persona sin experiencia previa en Event Sourcing, y el costo de dos modelos (proyecciones,
replay, versionado de eventos, snapshots) no es proporcional a un dashboard de tres consultas
y una tabla de historial.

**Conclusión.** No se justifica CQRS completo ni Event Sourcing completo. Se implementa una
extensión liviana: tres consultas agregadas en el mismo puerto `HallazgoRepositoryPort` y una
bitácora append-only (`historial_cambios_estado`) que coexiste con el estado actual. Lo que
haría cambiar esta conclusión está al final, en las conclusiones.

### Extensión implementada

| Requisito | Implementación |
|---|---|
| Dashboard | `HallazgoRepositoryPort` gana `contarPorSeveridad`, `contarPorEstado` y `promedioDiasCierrePorArea`. `HallazgoJpaRepository` las resuelve con dos JPQL `GROUP BY` y una consulta nativa (`DATEDIFF` es una función de H2 y no existe en JPQL). `ObtenerDashboardAuditoriaService` las junta en `DashboardAuditoriaView`. |
| Trazabilidad | `HistorialAuditoriaPort` con `HistorialAuditoriaAdapter` sobre la tabla `historial_cambios_estado`. Cada caso de uso que cambia el estado (y el registro inicial) agrega una fila con estado anterior, estado nuevo, motivo, usuario (cabecera `X-Usuario`) y fecha. |
| Misma transacción | El círculo Use Cases no puede usar `@Transactional`. `AuditoriaConfiguration` envuelve cada caso de uso de escritura en un `TransactionTemplate`, así el `UPDATE` del hallazgo y el `INSERT` de la bitácora se confirman juntos. `TransaccionBitacoraTest` fuerza un fallo en la bitácora y comprueba que el hallazgo sigue `ABIERTO`. |
| No alterable | `HistorialCambioEstadoJpaEntity` es `@Immutable`, sin setters y con columnas `updatable = false`. Su repositorio extiende `Repository` (no `JpaRepository`) y solo declara `save` y una consulta: no hay `delete` disponible. |

`HallazgoJpaEntity` sigue siendo la única fuente del estado actual. La bitácora se lee solo
para mostrarla en `/historial`; nunca se usa para reconstruir el agregado.

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/hallazgos/dashboard` | 200 con `porSeveridad`, `porEstado` y `promedioDiasCierrePorArea` |
| GET | `/api/hallazgos/{id}/historial` | 200 con los cambios en orden cronológico; 404 si el hallazgo no existe |

```bash
curl http://localhost:8080/api/hallazgos/dashboard
curl http://localhost:8080/api/hallazgos/{id}/historial
curl -X PATCH http://localhost:8080/api/hallazgos/{id}/cerrar -H "X-Usuario: jefe.infra"
```

Ejemplo de respuesta del historial de un hallazgo que se abrió, se remedió, se cerró y se
reabrió:

```json
[
  {"estadoAnterior":null,"estadoNuevo":"ABIERTO","motivo":"Registro del hallazgo","usuario":"auditor.lopez","fecha":"..."},
  {"estadoAnterior":"ABIERTO","estadoNuevo":"EN_REMEDIACION","motivo":"Plan de remediacion a cargo de Equipo de Infraestructura","usuario":"jefe.infra","fecha":"..."},
  {"estadoAnterior":"EN_REMEDIACION","estadoNuevo":"CERRADO","motivo":"Cierre de remediacion","usuario":"jefe.infra","fecha":"..."},
  {"estadoAnterior":"CERRADO","estadoNuevo":"REABIERTO","motivo":"El escaneo de octubre volvio a encontrar la credencial","usuario":"auditor.lopez","fecha":"..."}
]
```

Pruebas de la Parte 2: `CasosDeUsoTest` (bitácora con fakes, sin Spring),
`ConsultasDashboardTest` (`@DataJpaTest` con fechas conocidas: TI cierra en 10 y 20 días →
promedio 15), `DashboardYHistorialApiTest` (una fila por transición, ninguna en una
transición rechazada, orden y contenido del historial) y `TransaccionBitacoraTest`.

### Evidencias de los endpoints

`generar-evidencias.ps1` compila el jar, levanta la API, recorre los checkpoints de ambas
partes con `curl -i` y guarda cada respuesta en [`docs/evidencias`](docs/evidencias):

| Archivo | Checkpoint |
|---|---|
| `01-registrar-hallazgo-201.txt` | registro con UUID |
| `03-registrar-sin-titulo-400.txt` | validación de entrada |
| `04-iniciar-remediacion-200.txt` | ABIERTO → EN_REMEDIACION |
| `05-cerrar-hallazgo-abierto-400.txt` | cerrar sin plan |
| `06-cerrar-...-200.txt` / `07-reabrir-...-200.txt` | cierre y reapertura |
| `08-reabrir-hallazgo-abierto-400.txt` | transición inválida |
| `09`, `10`, `11` | consulta por id, 404 y listado |
| `15-dashboard-200.txt` | dashboard con los tres indicadores |
| `16-historial-hallazgo-200.txt` | historial de las cuatro transiciones |
| `17-historial-inexistente-404.txt` | historial de un id inexistente |

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

### 3. CQRS / Event Sourcing completos frente a una extensión liviana

Se eligió la extensión liviana, por los criterios de la sección 7 aplicados en el análisis
de la Parte 2:

- **Escala:** decenas de operaciones al día; no hay una carga de lectura que escalar por
  separado.
- **Forma de las consultas:** tres agregaciones sobre una tabla, resueltas con
  `GROUP BY` en el mismo esquema. Un modelo de lectura propio duplicaría datos sin
  aportar nada.
- **Consistencia:** el comité espera ver el estado al momento de consultar; con una
  consulta directa la consistencia es inmediata y no hay proyección que mantener.
- **Costo:** CQRS completo significa un segundo modelo, un proyector, despliegue y
  esquema propios y la lógica para mantenerlos sincronizados. Es un costo permanente para
  resolver tres consultas.

Lo que se conserva del espíritu de CQRS es la separación lógica: el dashboard es un caso de
uso de solo lectura que no pasa por el agregado. Si mañana hiciera falta, se podría mover su
implementación a otra fuente de datos sin tocar los casos de uso de escritura.

### 4. Bitácora simple (`HistorialCambioEstado`) frente a un Event Store

Cumplimiento necesita ver la secuencia de cambios, no reconstruir el estado reproduciendo
eventos. Un Event Store haría que `HallazgoAuditoria` dejara de guardar su estado y se
reconstruyera por replay en cada lectura: un cambio de fondo en un agregado que ya funciona,
más versionado de eventos y probablemente snapshots. Las señales de sobre-ingeniería de la
sección 7.2 están presentes: no hay un experto de negocio que modele los eventos, el equipo
es una persona sin experiencia previa en Event Sourcing y el costo no guarda proporción con
el problema (un hallazgo tiene pocos cambios de estado en toda su vida).

La bitácora cumple lo que se pidió: es cronológica, registra quién, cuándo y de qué estado a
qué estado, se escribe en la misma transacción que el cambio y la aplicación no ofrece forma
de modificarla. Lo que no cubre es la protección frente a alguien con acceso directo a la
base de datos; para eso la medida correcta sería de infraestructura (permisos de solo
`INSERT`/`SELECT` sobre la tabla para el usuario de la aplicación, o copias a un
almacenamiento de solo escritura), no cambiar el modelo de persistencia.

## Herramientas utilizadas

- Java 17, Spring Boot 3.2, Spring Web, Spring Data JPA, Bean Validation, H2
- JUnit 5, Mockito, MockMvc, `@DataJpaTest`
- Maven Wrapper, curl, PowerShell, Git, GitHub

## Conclusiones

En la Parte 1 la regla de dependencia sirvió como herramienta para encontrar problemas: al
revisar que `usecase/` no dependiera de los adaptadores apareció que la consulta devolvía un
DTO web, y al probar la persistencia en todos los estados apareció que la reconstrucción por
transiciones no funcionaba con hallazgos reabiertos. La consecuencia menos obvia de Clean
Architecture fue la transacción: como los casos de uso no pueden usar `@Transactional`, hubo
que definirla en la configuración. En la Parte 2 los requisitos sonaban exactamente a CQRS y
Event Sourcing, pero al medirlos contra los criterios de la guía resultaron ser tres consultas
y una tabla de historial. La decisión se reconsideraría si el dashboard pasara a consultarse
de forma continua por muchos usuarios con agregaciones costosas (entonces una proyección de
lectura tendría sentido), o si Cumplimiento pidiera conocer el estado completo de un hallazgo
en una fecha pasada o alimentar otros sistemas con los cambios; en ese caso el historial ya
guardado sería el punto de partida para pasar a eventos.
