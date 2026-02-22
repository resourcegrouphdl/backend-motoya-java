# ESPECIFICACIÓN DE ENDPOINTS - MÓDULO DE CONTRATOS

**Módulo:** Contratos (Gestión de flujo documental)
**Stack:** Java 21 + Spring WebFlux + R2DBC + PostgreSQL
**Base URL:** `/api/v1`
**Fecha:** 2026-02-11

---

## TABLA DE CONTENIDOS

1. [Resumen de Endpoints](#1-resumen-de-endpoints)
2. [Flujo de Estados](#2-flujo-de-estados)
3. [Enums del Dominio](#3-enums-del-dominio)
4. [Detalle de Endpoints](#4-detalle-de-endpoints)
   - [4.1 Listar Contratos](#41-listar-contratos)
   - [4.2 Detalle de Contrato](#42-detalle-de-contrato)
   - [4.3 Crear Contrato Manual](#43-crear-contrato-manual)
   - [4.4 Validar Documento](#44-validar-documento-boucher-o-factura)
   - [4.5 Aprobar Contrato](#45-aprobar-contrato)
   - [4.6 Rechazar Contrato](#46-rechazar-contrato)
5. [Modelo de Datos (DTOs)](#5-modelo-de-datos-dtos)
6. [Esquema de Base de Datos](#6-esquema-de-base-de-datos-postgresql)
7. [Estructura Java (Hexagonal + DDD)](#7-estructura-java-hexagonal--ddd)
8. [Reglas de Negocio](#8-reglas-de-negocio)
9. [Códigos de Error](#9-códigos-de-error)

---

## 1. RESUMEN DE ENDPOINTS

| # | Método | Endpoint | Propósito |
|---|--------|----------|-----------|
| 1 | `GET` | `/api/v1/contratos/lista` | Listar contratos (vista tabla) |
| 2 | `GET` | `/api/v1/contratos/{id}` | Detalle completo de un contrato |
| 3 | `POST` | `/api/v1/contract` | Crear contrato manual |
| 4 | `PUT` | `/api/v1/contratos/{id}/documento/{tipo}/validar` | Validar boucher o factura |
| 5 | `PUT` | `/api/v1/contratos/{id}/aprobar` | Aprobar contrato completo |
| 6 | `PUT` | `/api/v1/contratos/{id}/rechazar` | Rechazar contrato |

---

## 2. FLUJO DE ESTADOS

### Máquina de estados del contrato

```
PENDIENTE_DOCUMENTOS  ──(tienda sube docs)──►  EN_VALIDACION
        │                                           │
        │                              ┌────────────┴────────────┐
        │                              ▼                         ▼
        │                    DOCUMENTOS_RECHAZADOS     GENERANDO_CONTRATO
        │                       │                          │
        │            (tienda corrige)               (sistema genera PDFs)
        │                       │                          │
        │                       ▼                          ▼
        │                 EN_VALIDACION            CONTRATO_GENERADO
        │                                                  │
        │                                        (cliente descarga)
        │                                                  │
        │                                                  ▼
        │                                          PENDIENTE_FIRMA
        │                                                  │
        │                                       (se sube evidencia)
        │                                                  │
        │                                                  ▼
        │                                              FIRMADO
        │                                                  │
        │                                         (admin confirma)
        │                                                  │
        │                                                  ▼
        │                                            COMPLETADO
        │
        └──────────(en cualquier punto)──────────►  CANCELADO
```

### Transiciones por fase

| Fase | Estado asociado | Acción que la activa |
|------|-----------------|----------------------|
| `INICIO` | `PENDIENTE_DOCUMENTOS` | Creación del contrato |
| `CARGA_DOCUMENTOS` | `PENDIENTE_DOCUMENTOS` | Tienda comienza a subir documentos |
| `VALIDACION_ADMIN` | `EN_VALIDACION` | Ambos documentos subidos |
| `GENERACION_CONTRATO` | `GENERANDO_CONTRATO` | Ambos documentos aprobados |
| `DESCARGA_CONTRATO` | `CONTRATO_GENERADO` | PDFs generados exitosamente |
| `FIRMA_CLIENTE` | `PENDIENTE_FIRMA` | Cliente descarga documentos |
| `FINALIZADO` | `COMPLETADO` | Admin confirma firma |

---

## 3. ENUMS DEL DOMINIO

### EstadoContrato

```java
public enum EstadoContrato {
    PENDIENTE_DOCUMENTOS,
    EN_VALIDACION,
    DOCUMENTOS_RECHAZADOS,
    GENERANDO_CONTRATO,
    CONTRATO_GENERADO,
    PENDIENTE_FIRMA,
    FIRMADO,
    COMPLETADO,
    CANCELADO
}
```

### FaseContrato

```java
public enum FaseContrato {
    INICIO,
    CARGA_DOCUMENTOS,
    VALIDACION_ADMIN,
    GENERACION_CONTRATO,
    DESCARGA_CONTRATO,
    FIRMA_CLIENTE,
    FINALIZADO
}
```

### EstadoValidacion

```java
public enum EstadoValidacion {
    PENDIENTE,
    EN_REVISION,
    APROBADO,
    RECHAZADO
}
```

---

## 4. DETALLE DE ENDPOINTS

### 4.1 Listar Contratos

```
GET /api/v1/contratos/lista
```

**Descripción:** Retorna una lista simplificada de contratos para mostrar en la tabla principal.

#### Query Params (opcionales)

| Param | Tipo | Default | Descripción |
|-------|------|---------|-------------|
| `estado` | `String` | — | Filtrar por `EstadoContrato` |
| `fase` | `String` | — | Filtrar por `FaseContrato` |
| `tiendaId` | `String` | — | Filtrar por ID de tienda |
| `busqueda` | `String` | — | Buscar por nombre, N° documento o N° contrato |
| `page` | `int` | `0` | Página (0-indexed) |
| `size` | `int` | `20` | Tamaño de página |

#### Response `200 OK`

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "numeroContrato": "CTR-2026-00001",
    "nombreCompleto": "Juan Pérez García",
    "numeroDocumento": "12345678",
    "tiendaNombre": "Motoya Lima Norte",
    "montoFinanciado": 15000.00,
    "estado": "EN_VALIDACION",
    "faseActual": "VALIDACION_ADMIN",
    "fechaCreacion": "2026-02-10T15:30:00"
  }
]
```

#### Controller

```java
@GetMapping("/contratos/lista")
public Flux<ContratoListItemDto> listarContratos(
    @RequestParam(required = false) String estado,
    @RequestParam(required = false) String fase,
    @RequestParam(required = false) String tiendaId,
    @RequestParam(required = false) String busqueda,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    return listarContratosUseCase.ejecutar(estado, fase, tiendaId, busqueda, page, size);
}
```

---

### 4.2 Detalle de Contrato

```
GET /api/v1/contratos/{id}
```

**Descripción:** Retorna el contrato completo con todos sus datos asociados.

#### Path Params

| Param | Tipo | Descripción |
|-------|------|-------------|
| `id` | `UUID` | ID del contrato |

#### Response `200 OK` — Objeto `Contrato` completo

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "numeroContrato": "CTR-2026-00001",
  "codigoEvaluacion": "EVA-2026-00015",

  "titular": {
    "nombres": "Juan",
    "apellidoPaterno": "Pérez",
    "apellidoMaterno": "García",
    "tipoDocumento": "DNI",
    "numeroDocumento": "12345678",
    "fechaNacimiento": "1990-05-15",
    "estadoCivil": "SOLTERO",
    "telefono": "987654321",
    "email": "juan@email.com",
    "direccion": "Av. Lima 123",
    "distrito": "San Martín de Porres",
    "provincia": "Lima",
    "departamento": "Lima",
    "ocupacion": "Comerciante",
    "ingresoMensual": 3500.00
  },

  "fiador": {
    "nombres": "María",
    "apellidoPaterno": "López",
    "apellidoMaterno": "Soto",
    "tipoDocumento": "DNI",
    "numeroDocumento": "87654321",
    "telefono": "912345678",
    "direccion": "Jr. Cusco 456",
    "relacionConTitular": "HERMANA"
  },

  "tienda": {
    "id": "tienda-uuid",
    "nombreComercial": "Motoya Lima Norte",
    "razonSocial": "Motoya SAC",
    "ruc": "20123456789",
    "direccion": "Av. Tupac Amaru 1234",
    "telefono": "014567890",
    "email": "limanorte@motoya.com",
    "comisionPorcentaje": 5.0
  },

  "datosFinancieros": {
    "precioVehiculo": 18000.00,
    "montoCuotaInicial": 3000.00,
    "montoFinanciado": 15000.00,
    "numeroCuotas": 24,
    "tasaInteresAnual": 18.5,
    "montoCuotaMensual": 780.50,
    "montoTotalAPagar": 18732.00,
    "fechaInicioContrato": "2026-03-01",
    "fechaFinContrato": "2028-02-28",
    "diaVencimientoCuota": 15
  },

  "estado": "EN_VALIDACION",
  "faseActual": "VALIDACION_ADMIN",

  "boucherPagoInicial": {
    "id": "doc-uuid-1",
    "urlDocumento": "https://storage.googleapis.com/motoya/boucher_pago.pdf",
    "nombreArchivo": "boucher_pago.pdf",
    "tipoArchivo": "application/pdf",
    "tamanioBytes": 245000,
    "fechaSubida": "2026-02-10T14:00:00",
    "estadoValidacion": "PENDIENTE",
    "observacionesValidacion": null,
    "validadoPor": null,
    "fechaValidacion": null
  },

  "facturaVehiculo": {
    "id": "doc-uuid-2",
    "numeroFactura": "F001-00234",
    "urlDocumento": "https://storage.googleapis.com/motoya/factura.pdf",
    "nombreArchivo": "factura_vehiculo.pdf",
    "tipoArchivo": "application/pdf",
    "tamanioBytes": 312000,
    "fechaEmision": "2026-02-09",
    "fechaSubida": "2026-02-10T14:05:00",
    "marcaVehiculo": "Honda",
    "modeloVehiculo": "Wave 110",
    "anioVehiculo": 2026,
    "colorVehiculo": "Negro",
    "serieMotor": "HW110-2026-001234",
    "serieChasis": "CHASIS-2026-005678",
    "estadoValidacion": "PENDIENTE",
    "observacionesValidacion": null,
    "validadoPor": null,
    "fechaValidacion": null
  },

  "documentosGenerados": [],
  "cronogramaPagos": null,
  "evidenciasFirma": [],

  "fechaCreacion": "2026-02-10T15:30:00",
  "fechaActualizacion": "2026-02-10T15:30:00",
  "creadoPor": "admin-uuid",
  "modificadoPor": null,
  "observaciones": null,

  "notificacionesEnviadas": []
}
```

#### Response `404 Not Found`

```json
{
  "code": "CONTRATO_NOT_FOUND",
  "message": "Contrato no encontrado con id: 550e8400-..."
}
```

#### Controller

```java
@GetMapping("/contratos/{id}")
public Mono<ContratoResponse> obtenerContrato(@PathVariable UUID id) {
    return obtenerContratoUseCase.ejecutar(id);
}
```

---

### 4.3 Crear Contrato Manual

```
POST /api/v1/contract
```

**Descripción:** Crea un contrato que no pasó por el flujo de evaluación normal. Se crea con estado `PENDIENTE_DOCUMENTOS` y fase `INICIO`.

#### Request Body — `CrearContratoManualRequest`

```json
{
  "numeroSolicitud": "SOL-2026-00015",
  "solicitudId": "solicitud-uuid",

  "titularNombreCompleto": "Juan",
  "titularApellido": "Pérez García",
  "titularTipoDocumento": "DNI",
  "titularNumeroDocumento": "12345678",
  "titularDomicilio": "Av. Lima 123",
  "titularDistrito": "San Martín de Porres",
  "titularProvincia": "Lima",
  "titularDepartamento": "Lima",

  "fiadorNombreCompleto": "María López",
  "fiadorApellido": "Soto",
  "fiadorTipoDocumento": "DNI",
  "fiadorNumeroDocumento": "87654321",
  "fiadorDomicilio": "Jr. Cusco 456",
  "fiadorDistrito": "Breña",
  "fiadorProvincia": "Lima",
  "fiadorDepartamento": "Lima",

  "inicialNumeros": "3000",
  "numeroQuincenas": "48",
  "numeroMeses": "24",
  "montoCuotaQuincenal": "390.25",
  "precioTotal": "18000",

  "marcaProducto": "Honda",
  "modeloProducto": "Wave 110",

  "nombreDeLaTienda": "Motoya Lima Norte",
  "rucDeLaTienda": "20123456789",
  "nombreDelVendedor": "Carlos Rodríguez"
}
```

#### Validaciones del Request

| Campo | Regla |
|-------|-------|
| `numeroSolicitud` | Requerido, no vacío |
| `solicitudId` | Requerido, no vacío |
| `titularNombreCompleto` | Requerido, min 2 caracteres |
| `titularApellido` | Requerido, min 2 caracteres |
| `titularTipoDocumento` | Requerido, valores: `DNI`, `CE`, `PASAPORTE` |
| `titularNumeroDocumento` | Requerido, 8 dígitos para DNI |
| `titularDomicilio` | Requerido |
| `titularDistrito` | Requerido |
| `titularProvincia` | Requerido |
| `titularDepartamento` | Requerido |
| `fiador*` | Todos opcionales, pero si se envía uno se requieren todos |
| `inicialNumeros` | Requerido, numérico positivo |
| `numeroQuincenas` | Requerido, numérico positivo |
| `numeroMeses` | Requerido, numérico positivo |
| `montoCuotaQuincenal` | Requerido, numérico positivo |
| `precioTotal` | Requerido, numérico positivo |
| `marcaProducto` | Requerido |
| `modeloProducto` | Requerido |
| `nombreDeLaTienda` | Requerido |
| `rucDeLaTienda` | Requerido, 11 dígitos |
| `nombreDelVendedor` | Requerido |

#### Response `201 Created` — Objeto `Contrato` completo

(Mismo formato que el endpoint de detalle, con estado inicial `PENDIENTE_DOCUMENTOS`)

#### Response `400 Bad Request`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Datos inválidos",
  "errors": [
    "titularNumeroDocumento: debe tener 8 dígitos para DNI",
    "precioTotal: debe ser un valor numérico positivo"
  ]
}
```

#### Controller

```java
@PostMapping("/contract")
@ResponseStatus(HttpStatus.CREATED)
public Mono<ContratoResponse> crearContratoManual(
    @Valid @RequestBody CrearContratoManualRequest request
) {
    return crearContratoManualUseCase.ejecutar(request);
}
```

---

### 4.4 Validar Documento (Boucher o Factura)

```
PUT /api/v1/contratos/{id}/documento/{tipo}/validar
```

**Descripción:** Aprueba o rechaza un documento subido por la tienda (boucher de pago inicial o factura del vehículo).

#### Path Params

| Param | Tipo | Valores válidos |
|-------|------|-----------------|
| `id` | `UUID` | ID del contrato |
| `tipo` | `String` | `boucher` o `factura` |

#### Request Body — `ValidarDocumentoRequest`

```json
{
  "contratoId": "550e8400-e29b-41d4-a716-446655440000",
  "tipoDocumento": "BOUCHER",
  "aprobado": true,
  "observaciones": "Documento correcto, monto coincide"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `contratoId` | `String` | Sí | ID del contrato (debe coincidir con path) |
| `tipoDocumento` | `String` | Sí | `BOUCHER` o `FACTURA` |
| `aprobado` | `boolean` | Sí | `true` = aprobar, `false` = rechazar |
| `observaciones` | `String` | Solo si rechazado | Motivo del rechazo |

#### Response `200 OK` — Objeto `Contrato` actualizado

#### Lógica de negocio

```
SI aprobado == true:
    documento.estadoValidacion = APROBADO
    documento.validadoPor = usuario_actual
    documento.fechaValidacion = ahora

    SI ambos documentos (boucher Y factura) están APROBADOS:
        contrato.estado = GENERANDO_CONTRATO
        contrato.faseActual = GENERACION_CONTRATO
        → Disparar generación automática de PDFs

SI aprobado == false:
    documento.estadoValidacion = RECHAZADO
    documento.observacionesValidacion = observaciones
    documento.validadoPor = usuario_actual
    documento.fechaValidacion = ahora
    contrato.estado = DOCUMENTOS_RECHAZADOS
    → Notificar a la tienda para que corrija
```

#### Response `400 Bad Request`

```json
{
  "code": "INVALID_DOCUMENT_TYPE",
  "message": "Tipo de documento inválido. Use 'BOUCHER' o 'FACTURA'"
}
```

#### Response `409 Conflict`

```json
{
  "code": "DOCUMENT_ALREADY_VALIDATED",
  "message": "El documento ya fue validado previamente"
}
```

#### Controller

```java
@PutMapping("/contratos/{id}/documento/{tipo}/validar")
public Mono<ContratoResponse> validarDocumento(
    @PathVariable UUID id,
    @PathVariable String tipo,
    @Valid @RequestBody ValidarDocumentoRequest request
) {
    return validarDocumentoUseCase.ejecutar(id, tipo, request);
}
```

---

### 4.5 Aprobar Contrato

```
PUT /api/v1/contratos/{id}/aprobar
```

**Descripción:** Aprueba el contrato completo tras la validación de documentos. Dispara la generación de documentos PDF.

#### Path Params

| Param | Tipo | Descripción |
|-------|------|-------------|
| `id` | `UUID` | ID del contrato |

#### Request Body

```json
{}
```

(Body vacío)

#### Response `200 OK` — Objeto `Contrato` actualizado

#### Precondiciones

- Ambos documentos (boucher y factura) deben estar en estado `APROBADO`
- El contrato debe estar en estado `EN_VALIDACION` o `GENERANDO_CONTRATO`

#### Lógica de negocio

```
1. Validar que boucher.estadoValidacion == APROBADO
2. Validar que factura.estadoValidacion == APROBADO
3. Generar documentos PDF:
   - Contrato de venta a crédito
   - Cronograma de pagos
   - Pagaré
   - Carta de instrucción
4. Calcular cronograma de cuotas (sistema francés)
5. Actualizar estado:
   contrato.estado = CONTRATO_GENERADO
   contrato.faseActual = DESCARGA_CONTRATO
6. Guardar documentos generados en Cloud Storage
7. Registrar URLs de documentos generados
```

#### Response `422 Unprocessable Entity`

```json
{
  "code": "DOCUMENTS_NOT_APPROVED",
  "message": "No se puede aprobar el contrato: documentos pendientes de validación"
}
```

#### Controller

```java
@PutMapping("/contratos/{id}/aprobar")
public Mono<ContratoResponse> aprobarContrato(@PathVariable UUID id) {
    return aprobarContratoUseCase.ejecutar(id);
}
```

---

### 4.6 Rechazar Contrato

```
PUT /api/v1/contratos/{id}/rechazar
```

**Descripción:** Rechaza/cancela un contrato en cualquier punto del flujo.

#### Path Params

| Param | Tipo | Descripción |
|-------|------|-------------|
| `id` | `UUID` | ID del contrato |

#### Request Body

```json
{
  "motivo": "Documentos no coinciden con los datos del titular"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `motivo` | `String` | Sí | Razón del rechazo (min 10 caracteres) |

#### Response `200 OK` — Objeto `Contrato` actualizado

#### Lógica de negocio

```
1. Validar que el contrato NO esté en estado COMPLETADO
2. Actualizar:
   contrato.estado = CANCELADO
   contrato.observaciones = motivo
   contrato.modificadoPor = usuario_actual
   contrato.fechaActualizacion = ahora
3. Notificar a la tienda
```

#### Response `409 Conflict`

```json
{
  "code": "CONTRACT_ALREADY_COMPLETED",
  "message": "No se puede rechazar un contrato ya completado"
}
```

#### Controller

```java
@PutMapping("/contratos/{id}/rechazar")
public Mono<ContratoResponse> rechazarContrato(
    @PathVariable UUID id,
    @Valid @RequestBody RechazarContratoRequest request
) {
    return rechazarContratoUseCase.ejecutar(id, request.getMotivo());
}
```

---

## 5. MODELO DE DATOS (DTOs)

### ContratoListItemDto (Response del listado)

```java
public record ContratoListItemDto(
    UUID id,
    String numeroContrato,
    String nombreCompleto,
    String numeroDocumento,
    String tiendaNombre,
    BigDecimal montoFinanciado,
    EstadoContrato estado,
    FaseContrato faseActual,
    LocalDateTime fechaCreacion
) {}
```

### CrearContratoManualRequest

```java
public record CrearContratoManualRequest(
    @NotBlank String numeroSolicitud,
    @NotBlank String solicitudId,

    // Titular
    @NotBlank String titularNombreCompleto,
    @NotBlank String titularApellido,
    @NotBlank String titularTipoDocumento,
    @NotBlank String titularNumeroDocumento,
    @NotBlank String titularDomicilio,
    @NotBlank String titularDistrito,
    @NotBlank String titularProvincia,
    @NotBlank String titularDepartamento,

    // Fiador (opcional)
    String fiadorNombreCompleto,
    String fiadorApellido,
    String fiadorTipoDocumento,
    String fiadorNumeroDocumento,
    String fiadorDomicilio,
    String fiadorDistrito,
    String fiadorProvincia,
    String fiadorDepartamento,

    // Financieros
    @NotBlank String inicialNumeros,
    @NotBlank String numeroQuincenas,
    @NotBlank String numeroMeses,
    @NotBlank String montoCuotaQuincenal,
    @NotBlank String precioTotal,

    // Producto
    @NotBlank String marcaProducto,
    @NotBlank String modeloProducto,

    // Tienda
    @NotBlank String nombreDeLaTienda,
    @NotBlank String rucDeLaTienda,
    @NotBlank String nombreDelVendedor
) {}
```

### ValidarDocumentoRequest

```java
public record ValidarDocumentoRequest(
    @NotBlank String contratoId,
    @NotBlank String tipoDocumento,  // "BOUCHER" | "FACTURA"
    boolean aprobado,
    String observaciones             // Requerido si aprobado == false
) {}
```

### RechazarContratoRequest

```java
public record RechazarContratoRequest(
    @NotBlank @Size(min = 10) String motivo
) {}
```

### ContratoResponse (Response completo)

```java
public record ContratoResponse(
    UUID id,
    String numeroContrato,
    String codigoEvaluacion,

    DatosTitularDto titular,
    DatosFiadorDto fiador,           // nullable
    TiendaInfoDto tienda,
    DatosFinancierosDto datosFinancieros,

    EstadoContrato estado,
    FaseContrato faseActual,

    BoucherPagoInicialDto boucherPagoInicial,  // nullable
    FacturaVehiculoDto facturaVehiculo,         // nullable

    List<DocumentoGeneradoDto> documentosGenerados,
    List<CuotaCronogramaDto> cronogramaPagos,   // nullable
    List<EvidenciaFirmaDto> evidenciasFirma,

    LocalDateTime fechaCreacion,
    LocalDateTime fechaActualizacion,
    String creadoPor,
    String modificadoPor,
    String observaciones,

    List<NotificacionDto> notificacionesEnviadas
) {}
```

### Sub-DTOs de Response

```java
public record DatosTitularDto(
    String nombres,
    String apellidoPaterno,
    String apellidoMaterno,
    String tipoDocumento,
    String numeroDocumento,
    LocalDate fechaNacimiento,
    String estadoCivil,
    String telefono,
    String email,
    String direccion,
    String distrito,
    String provincia,
    String departamento,
    String ocupacion,
    BigDecimal ingresoMensual
) {}

public record DatosFiadorDto(
    String nombres,
    String apellidoPaterno,
    String apellidoMaterno,
    String tipoDocumento,
    String numeroDocumento,
    String telefono,
    String direccion,
    String relacionConTitular
) {}

public record TiendaInfoDto(
    String id,
    String nombreComercial,
    String razonSocial,
    String ruc,
    String direccion,
    String telefono,
    String email,
    BigDecimal comisionPorcentaje
) {}

public record DatosFinancierosDto(
    BigDecimal precioVehiculo,
    BigDecimal montoCuotaInicial,
    BigDecimal montoFinanciado,
    int numeroCuotas,
    BigDecimal tasaInteresAnual,
    BigDecimal montoCuotaMensual,
    BigDecimal montoTotalAPagar,
    LocalDate fechaInicioContrato,
    LocalDate fechaFinContrato,
    int diaVencimientoCuota
) {}

public record BoucherPagoInicialDto(
    UUID id,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    long tamanioBytes,
    LocalDateTime fechaSubida,
    EstadoValidacion estadoValidacion,
    String observacionesValidacion,
    String validadoPor,
    LocalDateTime fechaValidacion
) {}

public record FacturaVehiculoDto(
    UUID id,
    String numeroFactura,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    long tamanioBytes,
    LocalDate fechaEmision,
    LocalDateTime fechaSubida,
    String marcaVehiculo,
    String modeloVehiculo,
    int anioVehiculo,
    String colorVehiculo,
    String serieMotor,
    String serieChasis,
    EstadoValidacion estadoValidacion,
    String observacionesValidacion,
    String validadoPor,
    LocalDateTime fechaValidacion
) {}

public record DocumentoGeneradoDto(
    UUID id,
    String tipoDocumento,   // "CONTRATO", "CRONOGRAMA", "PAGARE", "CARTA_INSTRUCCION"
    String urlDocumento,
    String nombreArchivo,
    LocalDateTime fechaGeneracion,
    String generadoPor,
    int versionDocumento,
    String descargadoPor,
    LocalDateTime fechaDescarga
) {}

public record CuotaCronogramaDto(
    int numeroCuota,
    LocalDate fechaVencimiento,
    BigDecimal montoCuota,
    BigDecimal montoCapital,
    BigDecimal montoInteres,
    BigDecimal saldoPendiente,
    String estadoPago,    // "PENDIENTE", "PAGADO", "VENCIDO", "EN_MORA"
    LocalDate fechaPago,
    BigDecimal montoPagado,
    int diasMora,
    BigDecimal montoMora
) {}

public record EvidenciaFirmaDto(
    UUID id,
    String tipoEvidencia,  // "FOTO", "VIDEO", "DOCUMENTO_ESCANEADO"
    String urlEvidencia,
    String nombreArchivo,
    String tipoArchivo,
    long tamanioBytes,
    LocalDateTime fechaSubida,
    String subidoPor,
    String descripcion
) {}

public record NotificacionDto(
    String tipo,
    String destinatario,
    LocalDateTime fecha,
    boolean exitoso
) {}
```

---

## 6. ESQUEMA DE BASE DE DATOS (PostgreSQL)


🧠 PROMPT PARA CLAUDE

Actúa como arquitecto senior de software, especialista en bases de datos NoSQL, Firebase Firestore, arquitectura hexagonal y aplicaciones reactivas con Spring WebFlux.

Tengo actualmente un esquema relacional en PostgreSQL altamente normalizado para la gestión de contratos (estructura incluida abajo).
Quiero migrarlo a Firebase Firestore.

⚠ Contexto importante:

La aplicación está construida con Spring Boot + WebFlux (reactivo).

La arquitectura es hexagonal (ports & adapters).

El contrato es el agregado principal.

Existen múltiples relaciones 1:1 y 1:N.

El sistema tiene workflow por estado y fase.

Se necesita un listado filtrable y paginado.

El rendimiento y costo en Firestore son críticos.

No se pueden usar JOINs.

Se necesita una estructura tipo “orquestador de agregado”.

🎯 Objetivos

Quiero que:

Analices el modelo SQL.

Propongas un rediseño óptimo para Firestore.

Definas:

Qué debe ir embebido.

Qué debe ir como subcolección.

Qué debe desnormalizarse.

Diseñes el modelo orientado a consultas (query-first modeling).

Propongas estructura para:

Documento raíz del contrato.

Subcolecciones.

Índices necesarios.

Propongas patrón de orquestación reactiva con WebFlux.

Sugieras mejoras arquitectónicas avanzadas.

Detectes riesgos de costos y rendimiento.

Propongas una estructura compatible con arquitectura hexagonal.

Sugieras si conviene modelo híbrido (colecciones globales para consultas transversales).
```sql
-- ═══════════════════════════════════════════════════════════════
-- TABLA PRINCIPAL: contratos
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contratos (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_contrato     VARCHAR(20) UNIQUE NOT NULL,
    codigo_evaluacion   VARCHAR(50),
    estado              VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_DOCUMENTOS',
    fase_actual         VARCHAR(30) NOT NULL DEFAULT 'INICIO',
    observaciones       TEXT,
    creado_por          VARCHAR(100) NOT NULL,
    modificado_por      VARCHAR(100),
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contratos_estado ON contratos(estado);
CREATE INDEX idx_contratos_fase ON contratos(fase_actual);
CREATE INDEX idx_contratos_fecha ON contratos(fecha_creacion DESC);

-- ═══════════════════════════════════════════════════════════════
-- TITULAR DEL CONTRATO
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_titulares (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id       UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    nombres           VARCHAR(100) NOT NULL,
    apellido_paterno  VARCHAR(100) NOT NULL,
    apellido_materno  VARCHAR(100),
    tipo_documento    VARCHAR(20) NOT NULL,
    numero_documento  VARCHAR(20) NOT NULL,
    fecha_nacimiento  DATE,
    estado_civil      VARCHAR(20),
    telefono          VARCHAR(20),
    email             VARCHAR(100),
    direccion         VARCHAR(200),
    distrito          VARCHAR(100),
    provincia         VARCHAR(100),
    departamento      VARCHAR(100),
    ocupacion         VARCHAR(100),
    ingreso_mensual   NUMERIC(12,2),
    UNIQUE(contrato_id)
);

CREATE INDEX idx_titular_documento ON contrato_titulares(numero_documento);

-- ═══════════════════════════════════════════════════════════════
-- FIADOR (OPCIONAL)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_fiadores (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id           UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    nombres               VARCHAR(100) NOT NULL,
    apellido_paterno      VARCHAR(100) NOT NULL,
    apellido_materno      VARCHAR(100),
    tipo_documento        VARCHAR(20) NOT NULL,
    numero_documento      VARCHAR(20) NOT NULL,
    telefono              VARCHAR(20),
    direccion             VARCHAR(200),
    relacion_con_titular  VARCHAR(50),
    UNIQUE(contrato_id)
);

-- ═══════════════════════════════════════════════════════════════
-- TIENDA ASOCIADA
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_tiendas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id         UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    tienda_id           VARCHAR(50) NOT NULL,
    nombre_comercial    VARCHAR(200) NOT NULL,
    razon_social        VARCHAR(200),
    ruc                 VARCHAR(11),
    direccion           VARCHAR(200),
    telefono            VARCHAR(20),
    email               VARCHAR(100),
    comision_porcentaje NUMERIC(5,2),
    UNIQUE(contrato_id)
);

CREATE INDEX idx_contrato_tienda ON contrato_tiendas(tienda_id);

-- ═══════════════════════════════════════════════════════════════
-- DATOS FINANCIEROS
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_financieros (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id           UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    precio_vehiculo       NUMERIC(12,2) NOT NULL,
    monto_cuota_inicial   NUMERIC(12,2) NOT NULL,
    monto_financiado      NUMERIC(12,2) NOT NULL,
    numero_cuotas         INT NOT NULL,
    tasa_interes_anual    NUMERIC(6,3) NOT NULL,
    monto_cuota_mensual   NUMERIC(12,2) NOT NULL,
    monto_total_a_pagar   NUMERIC(12,2) NOT NULL,
    fecha_inicio_contrato DATE NOT NULL,
    fecha_fin_contrato    DATE NOT NULL,
    dia_vencimiento_cuota INT NOT NULL,
    UNIQUE(contrato_id)
);

-- ═══════════════════════════════════════════════════════════════
-- DOCUMENTOS SUBIDOS (BOUCHER, FACTURA)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_documentos (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id               UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    tipo_documento            VARCHAR(20) NOT NULL,  -- 'BOUCHER', 'FACTURA'
    url_documento             VARCHAR(500) NOT NULL,
    nombre_archivo            VARCHAR(200) NOT NULL,
    tipo_archivo              VARCHAR(50),
    tamanio_bytes             BIGINT,
    fecha_subida              TIMESTAMP NOT NULL DEFAULT NOW(),
    estado_validacion         VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones_validacion  TEXT,
    validado_por              VARCHAR(100),
    fecha_validacion          TIMESTAMP,
    -- Campos específicos de factura
    numero_factura            VARCHAR(50),
    fecha_emision             DATE,
    marca_vehiculo            VARCHAR(50),
    modelo_vehiculo           VARCHAR(50),
    anio_vehiculo             INT,
    color_vehiculo            VARCHAR(30),
    serie_motor               VARCHAR(50),
    serie_chasis              VARCHAR(50),
    UNIQUE(contrato_id, tipo_documento)
);

-- ═══════════════════════════════════════════════════════════════
-- DOCUMENTOS GENERADOS (PDF CONTRATO, CRONOGRAMA, PAGARÉ)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_documentos_generados (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id        UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    tipo_documento     VARCHAR(30) NOT NULL,  -- 'CONTRATO','CRONOGRAMA','PAGARE','CARTA_INSTRUCCION'
    url_documento      VARCHAR(500) NOT NULL,
    nombre_archivo     VARCHAR(200) NOT NULL,
    fecha_generacion   TIMESTAMP NOT NULL DEFAULT NOW(),
    generado_por       VARCHAR(100) NOT NULL,
    version_documento  INT NOT NULL DEFAULT 1,
    descargado_por     VARCHAR(100),
    fecha_descarga     TIMESTAMP
);

CREATE INDEX idx_doc_gen_contrato ON contrato_documentos_generados(contrato_id);

-- ═══════════════════════════════════════════════════════════════
-- CRONOGRAMA DE PAGOS (CUOTAS)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_cuotas (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id        UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    numero_cuota       INT NOT NULL,
    fecha_vencimiento  DATE NOT NULL,
    monto_cuota        NUMERIC(12,2) NOT NULL,
    monto_capital      NUMERIC(12,2) NOT NULL,
    monto_interes      NUMERIC(12,2) NOT NULL,
    saldo_pendiente    NUMERIC(12,2) NOT NULL,
    estado_pago        VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_pago         DATE,
    monto_pagado       NUMERIC(12,2),
    dias_mora          INT DEFAULT 0,
    monto_mora         NUMERIC(12,2) DEFAULT 0,
    UNIQUE(contrato_id, numero_cuota)
);

CREATE INDEX idx_cuotas_contrato ON contrato_cuotas(contrato_id);
CREATE INDEX idx_cuotas_estado ON contrato_cuotas(estado_pago);

-- ═══════════════════════════════════════════════════════════════
-- EVIDENCIAS DE FIRMA
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_evidencias_firma (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id     UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    tipo_evidencia  VARCHAR(30) NOT NULL,  -- 'FOTO', 'VIDEO', 'DOCUMENTO_ESCANEADO'
    url_evidencia   VARCHAR(500) NOT NULL,
    nombre_archivo  VARCHAR(200) NOT NULL,
    tipo_archivo    VARCHAR(50),
    tamanio_bytes   BIGINT,
    fecha_subida    TIMESTAMP NOT NULL DEFAULT NOW(),
    subido_por      VARCHAR(100) NOT NULL,
    descripcion     TEXT
);

CREATE INDEX idx_evidencia_contrato ON contrato_evidencias_firma(contrato_id);

-- ═══════════════════════════════════════════════════════════════
-- NOTIFICACIONES ENVIADAS
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE contrato_notificaciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_id     UUID NOT NULL REFERENCES contratos(id) ON DELETE CASCADE,
    tipo            VARCHAR(50) NOT NULL,
    destinatario    VARCHAR(200) NOT NULL,
    fecha           TIMESTAMP NOT NULL DEFAULT NOW(),
    exitoso         BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_notif_contrato ON contrato_notificaciones(contrato_id);
```

### Query para el listado (JOIN para ContratoListItem)

```sql
SELECT
    c.id,
    c.numero_contrato,
    CONCAT(t.nombres, ' ', t.apellido_paterno, ' ', COALESCE(t.apellido_materno, '')) AS nombre_completo,
    t.numero_documento,
    ti.nombre_comercial AS tienda_nombre,
    f.monto_financiado,
    c.estado,
    c.fase_actual,
    c.fecha_creacion
FROM contratos c
    INNER JOIN contrato_titulares t ON t.contrato_id = c.id
    INNER JOIN contrato_tiendas ti ON ti.contrato_id = c.id
    INNER JOIN contrato_financieros f ON f.contrato_id = c.id
WHERE (:estado IS NULL OR c.estado = :estado)
  AND (:fase IS NULL OR c.fase_actual = :fase)
  AND (:tiendaId IS NULL OR ti.tienda_id = :tiendaId)
ORDER BY c.fecha_creacion DESC
LIMIT :size OFFSET :page * :size;
```

📌 Requisitos específicos

El listado actual en SQL usa JOIN entre:

contratos

contrato_titulares

contrato_tiendas

contrato_financieros

Quiero eliminar JOINs y optimizarlo en Firestore.

Hay cronograma de cuotas.

Hay documentos subidos.

Hay documentos generados.

Hay evidencias.

Hay notificaciones.

Hay workflow con estado y fase.

📤 Output esperado

Responde con:

🔥 Diseño final de colecciones en Firestore.

📄 Ejemplo JSON completo del documento principal.

📂 Subcolecciones recomendadas.

🧠 Estrategia de desnormalización.

⚡ Estrategia reactiva para WebFlux.

🏗 Adaptación a arquitectura hexagonal.

💰 Análisis de costos en Firestore.

🚀 Mejoras avanzadas (event sourcing, CQRS opcional).

Riesgos y cómo mitigarlos.

No quiero explicación teórica básica de Firestore.
Quiero diseño arquitectónico serio, listo para producción.
---



## 7. REGLAS DE NEGOCIO

| Regla | Descripción |
|-------|-------------|
| **RN-01** | Un contrato se crea con estado `PENDIENTE_DOCUMENTOS` y fase `INICIO` |
| **RN-02** | Solo se puede validar documentos si el contrato está en `EN_VALIDACION` |
| **RN-03** | Si se rechaza un documento, el contrato pasa a `DOCUMENTOS_RECHAZADOS` |
| **RN-04** | Solo se puede aprobar el contrato si ambos documentos están `APROBADO` |
| **RN-05** | Al aprobar, se generan automáticamente: contrato PDF, cronograma, pagaré y carta de instrucción |
| **RN-06** | El cronograma se calcula con sistema francés (cuota fija) |
| **RN-07** | Un contrato `COMPLETADO` no se puede rechazar ni cancelar |
| **RN-08** | El número de contrato se genera automáticamente: `CTR-{año}-{secuencial 5 dígitos}` |
| **RN-09** | Todos los cambios de estado deben registrar `modificadoPor` y `fechaActualizacion` |
| **RN-10** | Al rechazar documentos, se debe notificar a la tienda (campo `observaciones` obligatorio) |

---

## 9. CÓDIGOS DE ERROR

| Código HTTP | Code | Mensaje |
|-------------|------|---------|
| `400` | `VALIDATION_ERROR` | Datos de entrada inválidos |
| `400` | `INVALID_DOCUMENT_TYPE` | Tipo de documento no reconocido |
| `404` | `CONTRATO_NOT_FOUND` | Contrato no encontrado |
| `409` | `DOCUMENT_ALREADY_VALIDATED` | El documento ya fue validado |
| `409` | `CONTRACT_ALREADY_COMPLETED` | No se puede modificar un contrato completado |
| `422` | `DOCUMENTS_NOT_APPROVED` | Documentos pendientes de aprobación |
| `422` | `INVALID_STATE_TRANSITION` | Transición de estado no permitida |
| `500` | `PDF_GENERATION_ERROR` | Error al generar documentos PDF |
| `500` | `STORAGE_ERROR` | Error al almacenar archivo en Cloud Storage |
