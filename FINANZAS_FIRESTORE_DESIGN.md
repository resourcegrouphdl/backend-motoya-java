# Módulo Finanzas — Diseño de Base de Datos Firestore

> **Stack:** Cloud Firestore (modo Native) + Spring Boot WebFlux (Project Reactor)
> **SDK Java:** `firebase-admin` + `google-cloud-firestore` → adaptar `ApiFuture` a `Mono/Flux`
> **Estrategia general:** Subcollections para entidades con operaciones independientes, denormalización selectiva para queries frecuentes, counters separados para KPIs del dashboard.

---

## Índice

1. [Principios de Diseño](#1-principios-de-diseño)
2. [Árbol de Colecciones](#2-árbol-de-colecciones)
3. [Esquemas de Documentos](#3-esquemas-de-documentos)
4. [Índices Compuestos](#4-índices-compuestos)
5. [Reglas de Seguridad](#5-reglas-de-seguridad)
6. [Patrones de Query por Endpoint](#6-patrones-de-query-por-endpoint)
7. [Integración WebFlux](#7-integración-webflux)
8. [Scheduler de Recálculo de Estados](#8-scheduler-de-recálculo-de-estados)
9. [Estrategia de Counters y KPIs](#9-estrategia-de-counters-y-kpis)
10. [Consideraciones de Costo y Performance](#10-consideraciones-de-costo-y-performance)

---

## 1. Principios de Diseño

### Decisiones arquitectónicas clave

| Decisión | Elección | Justificación |
|---|---|---|
| Pagos de factura (`PagoFactura`) | **Subcollection** `/facturas/{id}/pagos` | Necesitamos `collectionGroup('pagos')` para queries de alertas cross-factura |
| Cuotas de cuenta (`CuotaCuenta`) | **Subcollection** `/cuentas_pagar/{id}/cuotas` | Hasta 36 cuotas, actualizaciones individuales frecuentes |
| Comisiones | **Top-level collection** `/comisiones` | Queries por período y tienda independientes de otras entidades |
| Alertas | **Computadas on-the-fly** desde subcollections | `collectionGroup` + WebFlux `Flux.merge()` elimina redundancia |
| KPIs del dashboard | **Documento contador** `/finanzas_kpis/current` | Evita aggregation queries costosas en cada request |
| Estados derivados | **Almacenados + recalculados** | Almacenados para queries rápidas; recalculados por scheduler y en mutaciones |

### Regla de denormalización
Se desnormalizan **campos de nombre** (tiendaNombre, clienteNombre, vendedorNombre) para evitar joins en lecturas. Cuando cambie el nombre de una tienda/vendedor, actualizar con un batch write.

---

## 2. Árbol de Colecciones

```
Firestore
│
├── facturas/                          ← Facturas de tiendas
│   ├── {facturaId}                    ← Documento factura
│   │   └── pagos/                     ← Subcollection: 2 documentos (P1 y P2)
│   │       ├── {pagoId}-P1
│   │       └── {pagoId}-P2
│   └── ...
│
├── cuentas_pagar/                     ← Cuentas por pagar operativas
│   ├── {cuentaId}                     ← Documento cuenta
│   │   └── cuotas/                    ← Subcollection: 1..36 documentos
│   │       ├── {cuotaId}-C1
│   │       └── {cuotaId}-CN
│   └── ...
│
├── comisiones/                        ← Comisiones de vendedores
│   └── {comisionId}
│
├── tiendas/                           ← Catálogo de tiendas (referencia)
│   └── {tiendaId}
│
├── vendedores/                        ← Catálogo de vendedores (referencia)
│   └── {vendedorId}
│
└── finanzas_kpis/                     ← Contadores para dashboard
    └── current                        ← Documento único de KPIs
```

> **¿Por qué subcollection para pagos y cuotas?**
> Firestore permite `collectionGroup('pagos').where(...)` y `collectionGroup('cuotas').where(...)` para hacer queries de alertas sobre todas las facturas/cuentas en una sola operación, sin necesidad de leer documentos padre.

---

## 3. Esquemas de Documentos

### 3.1 `/facturas/{facturaId}`

```json
{
  "id":             "FAC-001",
  "numero":         "F001-00123",
  "tiendaId":       "T-001",
  "tiendaNombre":   "Tienda Lima Norte",
  "ventaId":        "VTA-101",
  "clienteNombre":  "Roberto Quispe Mamani",
  "motoModelo":     "Bajaj Pulsar 150",
  "montoTotal":     8500.00,
  "fechaFactura":   "2026-02-12",
  "condicionPago":  15,

  "estado":         "VENCIDO",

  "creadoEn":       "2026-02-12T09:00:00Z",
  "actualizadoEn":  "2026-03-01T14:30:00Z",
  "creadoPor":      "uid-admin-001",

  "_alertaActiva":  true,
  "_tieneVencidos": true
}
```

> **Campos `_alertaActiva` y `_tieneVencidos`**: prefijados con `_` para indicar que son campos internos de query. Se actualizan en cada mutación sobre los pagos hijos. Permiten filtrar facturas con alertas sin usar collectionGroup.

**Índices simples en esta colección:**
- `tiendaId ASC`
- `estado ASC`
- `fechaFactura DESC`
- `_alertaActiva ASC`

---

### 3.2 `/facturas/{facturaId}/pagos/{pagoId}`

```json
{
  "id":              "FAC-001-P1",
  "facturaId":       "FAC-001",
  "numero":          1,
  "concepto":        "INICIAL",
  "monto":           1700.00,
  "fechaProgramada": "2026-02-14",
  "fechaPago":       "2026-02-13",
  "estado":          "PAGADO",
  "voucherUrl":      "https://storage.googleapis.com/motoya-docs/vouchers/FAC-001-P1.pdf",
  "metodoPago":      "TRANSFERENCIA",

  "tiendaId":        "T-001",
  "tiendaNombre":    "Tienda Lima Norte",
  "clienteNombre":   "Roberto Quispe Mamani",

  "actualizadoEn":   "2026-02-13T11:20:00Z"
}
```

> **Campos denormalizados** `tiendaId`, `tiendaNombre`, `clienteNombre`: necesarios para que el resultado de `collectionGroup('pagos')` tenga suficiente info para construir el mensaje de alerta sin leer el documento padre.

**Índices en esta subcollection (necesarios para collectionGroup):**
- `estado ASC, fechaProgramada ASC`
- `fechaProgramada ASC`

---

### 3.3 `/cuentas_pagar/{cuentaId}`

```json
{
  "id":               "CTA-004",
  "tipo":             "PROVEEDOR",
  "proveedor":        "Bajaj Auto Perú SAC",
  "descripcion":      "Lote 5 motos Bajaj Pulsar 150",
  "numeroDocumento":  "F-2026-001",
  "montoTotal":       45000.00,
  "numeroCuotas":     3,
  "estado":           "PENDIENTE",
  "fechaVencimiento": "2026-03-26",
  "creadoEn":         "2026-02-04T10:00:00Z",
  "actualizadoEn":    "2026-02-24T08:00:00Z",
  "creadoPor":        "uid-admin-001",

  "_alertaActiva":    false,
  "_tieneVencidos":   false
}
```

---

### 3.4 `/cuentas_pagar/{cuentaId}/cuotas/{cuotaId}`

```json
{
  "id":               "CTA-004-C2",
  "cuentaId":         "CTA-004",
  "numero":           2,
  "monto":            15000.00,
  "fechaVencimiento": "2026-03-26",
  "fechaPago":        null,
  "estado":           "PENDIENTE",

  "proveedor":        "Bajaj Auto Perú SAC",
  "descripcion":      "Lote 5 motos Bajaj Pulsar 150",
  "tipo":             "PROVEEDOR",

  "actualizadoEn":    "2026-02-24T08:00:00Z"
}
```

---

### 3.5 `/comisiones/{comisionId}`

```json
{
  "id":             "COM-001",
  "vendedorId":     "V-001",
  "vendedorNombre": "Carlos Quispe Mamani",
  "tiendaId":       "T-001",
  "tiendaNombre":   "Tienda Lima Norte",
  "periodoInicio":  "2026-02-27",
  "periodoFin":     "2026-03-05",
  "totalVentas":    4,
  "montoComision":  480.00,
  "estado":         "PENDIENTE",
  "pagadoEn":       null,
  "creadoEn":       "2026-03-06T00:01:00Z",
  "actualizadoEn":  "2026-03-06T00:01:00Z"
}
```

---

### 3.6 `/finanzas_kpis/current` (Documento único de KPIs)

```json
{
  "totalFacturasPendientes":  6,
  "montoFacturasPendientes":  42300.00,
  "pagosTiendaHoy":           1,
  "pagosVencidos":            3,
  "egresosDelMes":            13369.00,
  "comisionesPendientes":     4,
  "ultimaActualizacion":      "2026-03-06T00:05:00Z",

  "_mesEgresos":              "2026-03"
}
```

> **Actualización**: este documento se actualiza en cada mutación que cambie estados (registrar pago, crear cuenta, etc.) usando **transacciones atómicas** de Firestore. El scheduler nocturno también lo recalcula completo.

---

### 3.7 `/tiendas/{tiendaId}` (Catálogo de referencia)

```json
{
  "id":       "T-001",
  "nombre":   "Tienda Lima Norte",
  "activa":   true,
  "direccion": "Av. Túpac Amaru 1200, Lima",
  "contacto":  "tiendalimanorte@email.com"
}
```

---

## 4. Índices Compuestos

Los índices compuestos deben definirse en `firestore.indexes.json`. Los siguientes son **obligatorios** para que los queries del módulo funcionen.

```json
{
  "indexes": [

    // ── Facturas ────────────────────────────────────────────────────
    {
      "collectionGroup": "facturas",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "tiendaId",     "order": "ASCENDING" },
        { "fieldPath": "estado",       "order": "ASCENDING" },
        { "fieldPath": "fechaFactura", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "facturas",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "estado",       "order": "ASCENDING" },
        { "fieldPath": "fechaFactura", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "facturas",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "_alertaActiva", "order": "ASCENDING" },
        { "fieldPath": "fechaFactura",  "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "facturas",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "fechaFactura", "order": "ASCENDING" },
        { "fieldPath": "fechaFactura", "order": "DESCENDING" }
      ]
    },

    // ── Pagos (collectionGroup — CRÍTICO para alertas) ───────────────
    {
      "collectionGroup": "pagos",
      "queryScope": "COLLECTION_GROUP",
      "fields": [
        { "fieldPath": "estado",          "order": "ASCENDING" },
        { "fieldPath": "fechaProgramada", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "pagos",
      "queryScope": "COLLECTION_GROUP",
      "fields": [
        { "fieldPath": "tiendaId",        "order": "ASCENDING" },
        { "fieldPath": "fechaProgramada", "order": "ASCENDING" }
      ]
    },

    // ── Cuentas por Pagar ────────────────────────────────────────────
    {
      "collectionGroup": "cuentas_pagar",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "tipo",            "order": "ASCENDING" },
        { "fieldPath": "estado",          "order": "ASCENDING" },
        { "fieldPath": "fechaVencimiento","order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "cuentas_pagar",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "_alertaActiva",   "order": "ASCENDING" },
        { "fieldPath": "fechaVencimiento","order": "ASCENDING" }
      ]
    },

    // ── Cuotas (collectionGroup — para alertas de cuentas) ──────────
    {
      "collectionGroup": "cuotas",
      "queryScope": "COLLECTION_GROUP",
      "fields": [
        { "fieldPath": "estado",           "order": "ASCENDING" },
        { "fieldPath": "fechaVencimiento", "order": "ASCENDING" }
      ]
    },

    // ── Comisiones ───────────────────────────────────────────────────
    {
      "collectionGroup": "comisiones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "tiendaId",     "order": "ASCENDING" },
        { "fieldPath": "estado",       "order": "ASCENDING" },
        { "fieldPath": "periodoFin",   "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "comisiones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "estado",       "order": "ASCENDING" },
        { "fieldPath": "periodoFin",   "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "comisiones",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "periodoInicio", "order": "ASCENDING" },
        { "fieldPath": "periodoFin",    "order": "ASCENDING" }
      ]
    }
  ]
}
```

---

## 5. Reglas de Seguridad

```javascript
// firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // ── Helpers ────────────────────────────────────────────────────
    function isAuthenticated() {
      return request.auth != null;
    }

    function isAdmin() {
      return isAuthenticated()
        && request.auth.token.role in ['ADMIN', 'SUPER_ADMIN'];
    }

    function isFinanzasUser() {
      return isAuthenticated()
        && request.auth.token.role in ['ADMIN', 'SUPER_ADMIN', 'FINANZAS'];
    }

    // ── Facturas ───────────────────────────────────────────────────
    match /facturas/{facturaId} {
      allow read:   if isFinanzasUser();
      allow create: if isAdmin();
      allow update: if isAdmin();
      allow delete: if false;  // Nunca borrar facturas

      match /pagos/{pagoId} {
        allow read:   if isFinanzasUser();
        allow create: if isAdmin();
        allow update: if isAdmin();
        allow delete: if false;
      }
    }

    // ── Cuentas por Pagar ──────────────────────────────────────────
    match /cuentas_pagar/{cuentaId} {
      allow read:   if isFinanzasUser();
      allow create: if isAdmin();
      allow update: if isAdmin();
      allow delete: if false;

      match /cuotas/{cuotaId} {
        allow read:   if isFinanzasUser();
        allow create: if isAdmin();
        allow update: if isAdmin();
        allow delete: if false;
      }
    }

    // ── Comisiones ─────────────────────────────────────────────────
    match /comisiones/{comisionId} {
      allow read:   if isFinanzasUser();
      allow create: if isAdmin();
      allow update: if isAdmin();
      allow delete: if false;
    }

    // ── KPIs (solo lectura para frontend, escritura solo backend) ──
    match /finanzas_kpis/{docId} {
      allow read:   if isFinanzasUser();
      allow write:  if false;  // Solo el backend server SDK escribe
    }

    // ── Catálogos ──────────────────────────────────────────────────
    match /tiendas/{tiendaId} {
      allow read:   if isAuthenticated();
      allow write:  if isAdmin();
    }
  }
}
```

---

## 6. Patrones de Query por Endpoint

### GET `/api/facturas` (con filtros)

```
Colección:  facturas
Query:
  WHERE tiendaId == :tiendaId          (si se envía)
  WHERE estado   == :estado            (si se envía)
  WHERE fechaFactura >= :fechaDesde    (si se envía)
  WHERE fechaFactura <= :fechaHasta    (si se envía)
  ORDER BY fechaFactura DESC
  LIMIT 50  (paginado)

Luego: por cada factura, cargar subcollection pagos/ (2 docs por factura)
→ En WebFlux: Flux<Factura>.flatMap(f -> cargarPagos(f))
```

> ⚠️ **Importante**: si se filtra por `estado` Y `fechaFactura`, necesita el índice compuesto `(estado, fechaFactura DESC)`. Si se filtra por `tiendaId` Y `estado` Y `fechaFactura`, necesita el índice de 3 campos.

---

### GET `/api/finanzas/alertas`

```
Query 1 — Pagos de facturas con alerta:
  collectionGroup('pagos')
  WHERE estado IN ['VENCIDO', 'PROXIMO_VENCER']
  ORDER BY fechaProgramada ASC

Query 2 — Cuotas con alerta:
  collectionGroup('cuotas')
  WHERE estado IN ['VENCIDO', 'PENDIENTE']
  WHERE fechaVencimiento <= :fecha7DiasDesdeHoy
  ORDER BY fechaVencimiento ASC

→ Combinar en WebFlux: Flux.merge(query1, query2)
→ Mapear a AlertaFinanciera DTO
→ Ordenar: VENCIDO → HOY → PROXIMO
```

> **Nota sobre `estado IN`**: Firestore no soporta `IN` con arrays en queries `where`. Usar dos queries separadas y combinar:
> - Query 1a: `estado == 'VENCIDO'`
> - Query 1b: `estado == 'PROXIMO_VENCER'`
> - En WebFlux: `Flux.merge(query1a, query1b)`

---

### GET `/api/finanzas/dashboard`

```
1. Leer /finanzas_kpis/current  → 1 lectura (O(1))
2. Query pagos vencidos hoy para proximosPagos:
   collectionGroup('pagos')
   WHERE estado != 'PAGADO'
   WHERE fechaProgramada <= :hoy+7dias
   ORDER BY fechaProgramada ASC
   LIMIT 20
3. Combinar con cuotas próximas:
   collectionGroup('cuotas')
   WHERE estado == 'PENDIENTE'
   WHERE fechaVencimiento <= :hoy+7dias
   LIMIT 20
→ Mono.zip(kpis, proximosPagos, proximasCuotas)
```

---

### POST `/api/pagos` (registrar pago)

```
TRANSACCIÓN Firestore:
  1. Leer /facturas/{facturaId}/pagos/{pagoId}
     → validar que estado != PAGADO
  2. Actualizar el documento de pago:
     { estado: PAGADO, fechaPago: ..., metodoPago: ... }
  3. Leer todos los pagos de la factura:
     /facturas/{facturaId}/pagos/
  4. Calcular nuevo estado de la factura (lógica derivada)
  5. Actualizar /facturas/{facturaId}:
     { estado: nuevoEstado, _alertaActiva: ..., _tieneVencidos: ..., actualizadoEn: ... }
  6. Actualizar /finanzas_kpis/current (ver sección 9)

→ En WebFlux: Mono<Void> usando runTransaction()
```

---

### POST `/api/cuentas-pagar` (crear con cuotas)

```
BATCH WRITE Firestore:
  1. Crear /cuentas_pagar/{nuevaCuentaId}
  2. Para cada cuota generada (1..N):
     Crear /cuentas_pagar/{nuevaCuentaId}/cuotas/{cuotaId-CN}
  3. Actualizar /finanzas_kpis/current:
     { totalCuentasPendientes: increment(1) }

→ WriteBatch de Firestore (hasta 500 ops por batch)
→ Convertir a Mono<DocumentReference>
```

---

### POST `/api/cuentas-pagar/{cuentaId}/pagar/cuotas/{cuotaId}`

```
TRANSACCIÓN Firestore:
  1. Leer /cuentas_pagar/{cuentaId}/cuotas/{cuotaId}
     → validar estado != PAGADO
  2. Actualizar cuota: { estado: PAGADO, fechaPago: hoy }
  3. Leer todas las cuotas de la cuenta
  4. Calcular nuevo estado de la cuenta:
     - ALL PAGADO → PAGADO
     - SOME VENCIDO → VENCIDO
     - ELSE → PENDIENTE
  5. Calcular nueva fechaVencimiento (próxima cuota pendiente)
  6. Actualizar /cuentas_pagar/{cuentaId}:
     { estado, fechaVencimiento, _alertaActiva, actualizadoEn }
  7. Actualizar KPIs si la cuenta cambió a PAGADO
```

---

## 7. Integración WebFlux

### 7.1 Dependencias `pom.xml`

```xml
<!-- Firebase Admin SDK -->
<dependency>
  <groupId>com.google.firebase</groupId>
  <artifactId>firebase-admin</artifactId>
  <version>9.3.0</version>
</dependency>

<!-- Project Reactor (incluido en Spring WebFlux) -->
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-core</artifactId>
</dependency>
```

---

### 7.2 Adaptador `ApiFuture` → `Mono` (utilitario base)

```java
// FirestoreReactiveUtils.java
public class FirestoreReactiveUtils {

    /**
     * Convierte un ApiFuture<T> de Firestore en Mono<T> reactivo.
     */
    public static <T> Mono<T> toMono(ApiFuture<T> future) {
        return Mono.fromFuture(() -> {
            CompletableFuture<T> completable = new CompletableFuture<>();
            ApiFutures.addCallback(future, new ApiFutureCallback<T>() {
                @Override public void onSuccess(T result) { completable.complete(result); }
                @Override public void onFailure(Throwable t) { completable.completeExceptionally(t); }
            }, MoreExecutors.directExecutor());
            return completable;
        });
    }

    /**
     * Convierte QuerySnapshot en Flux<DocumentSnapshot>.
     */
    public static Flux<DocumentSnapshot> toFlux(ApiFuture<QuerySnapshot> future) {
        return toMono(future)
            .flatMapMany(snapshot -> Flux.fromIterable(snapshot.getDocuments()));
    }
}
```

---

### 7.3 Repositorio de Facturas

```java
// FacturaRepository.java
@Repository
public class FacturaRepository {

    private final Firestore db;

    // ── Listar facturas con filtros ─────────────────────────────────
    public Flux<FacturaDocument> findAll(FiltrosFactura filtros) {
        CollectionReference col = db.collection("facturas");
        Query query = col.orderBy("fechaFactura", Query.Direction.DESCENDING);

        if (filtros.getTiendaId() != null)
            query = query.whereEqualTo("tiendaId", filtros.getTiendaId());
        if (filtros.getEstado() != null)
            query = query.whereEqualTo("estado", filtros.getEstado().name());
        if (filtros.getFechaDesde() != null)
            query = query.whereGreaterThanOrEqualTo("fechaFactura", filtros.getFechaDesde());
        if (filtros.getFechaHasta() != null)
            query = query.whereLessThanOrEqualTo("fechaFactura", filtros.getFechaHasta());

        return FirestoreReactiveUtils.toFlux(query.get())
            .map(doc -> doc.toObject(FacturaDocument.class));
    }

    // ── Cargar factura con sus pagos ────────────────────────────────
    public Mono<FacturaTiendaDto> findByIdWithPagos(String facturaId) {
        Mono<FacturaDocument> facturaMono = toMono(
            db.collection("facturas").document(facturaId).get()
        ).map(snap -> snap.toObject(FacturaDocument.class));

        Flux<PagoDocument> pagosFlux = toFlux(
            db.collection("facturas").document(facturaId).collection("pagos").get()
        ).map(doc -> doc.toObject(PagoDocument.class));

        return Mono.zip(facturaMono, pagosFlux.collectList())
            .map(tuple -> FacturaTiendaDto.from(tuple.getT1(), tuple.getT2()));
    }

    // ── Listar facturas con pagos (para tabla) ──────────────────────
    public Flux<FacturaTiendaDto> findAllWithPagos(FiltrosFactura filtros) {
        return findAll(filtros)
            .flatMap(factura -> {
                Flux<PagoDocument> pagosFlux = toFlux(
                    db.collection("facturas").document(factura.getId())
                      .collection("pagos").orderBy("numero").get()
                ).map(doc -> doc.toObject(PagoDocument.class));

                return pagosFlux.collectList()
                    .map(pagos -> FacturaTiendaDto.from(factura, pagos));
            });
    }
}
```

---

### 7.4 Repositorio de Alertas (collectionGroup)

```java
// AlertasRepository.java
@Repository
public class AlertasRepository {

    private final Firestore db;

    // ── Alertas de facturas (collectionGroup sobre 'pagos') ─────────
    public Flux<AlertaFinanciera> getAlertasFacturas() {
        String hoy = LocalDate.now().toString();
        String en7Dias = LocalDate.now().plusDays(7).toString();

        // Query 1: VENCIDOS (fechaProgramada < hoy, no pagados)
        Flux<AlertaFinanciera> vencidos = toFlux(
            db.collectionGroup("pagos")
              .whereNotEqualTo("estado", "PAGADO")
              .whereLessThan("fechaProgramada", hoy)
              .get()
        ).map(doc -> mapPagoToAlerta(doc, "VENCIDO"));

        // Query 2: HOY
        Flux<AlertaFinanciera> hoyAlertas = toFlux(
            db.collectionGroup("pagos")
              .whereNotEqualTo("estado", "PAGADO")
              .whereEqualTo("fechaProgramada", hoy)
              .get()
        ).map(doc -> mapPagoToAlerta(doc, "HOY"));

        // Query 3: PROXIMOS (1..7 días)
        Flux<AlertaFinanciera> proximos = toFlux(
            db.collectionGroup("pagos")
              .whereNotEqualTo("estado", "PAGADO")
              .whereGreaterThan("fechaProgramada", hoy)
              .whereLessThanOrEqualTo("fechaProgramada", en7Dias)
              .get()
        ).map(doc -> mapPagoToAlerta(doc, "PROXIMO"));

        return Flux.merge(vencidos, hoyAlertas, proximos);
    }

    // ── Alertas de cuentas (collectionGroup sobre 'cuotas') ─────────
    public Flux<AlertaFinanciera> getAlertasCuentas() {
        String hoy = LocalDate.now().toString();
        String en7Dias = LocalDate.now().plusDays(7).toString();

        Flux<AlertaFinanciera> vencidas = toFlux(
            db.collectionGroup("cuotas")
              .whereEqualTo("estado", "VENCIDO")
              .get()
        ).map(doc -> mapCuotaToAlerta(doc, "VENCIDO"));

        Flux<AlertaFinanciera> proximas = toFlux(
            db.collectionGroup("cuotas")
              .whereEqualTo("estado", "PENDIENTE")
              .whereLessThanOrEqualTo("fechaVencimiento", en7Dias)
              .get()
        ).map(doc -> {
            String tipo = doc.getString("fechaVencimiento").equals(hoy) ? "HOY" : "PROXIMO";
            return mapCuotaToAlerta(doc, tipo);
        });

        return Flux.merge(vencidas, proximas);
    }

    // ── Combinar todas las alertas ───────────────────────────────────
    public Flux<AlertaFinanciera> getAllAlertas() {
        return Flux.merge(getAlertasFacturas(), getAlertasCuentas())
            .sort(Comparator.comparingInt(a -> ordenAlerta(a.getTipo())));
    }

    private int ordenAlerta(String tipo) {
        return switch (tipo) {
            case "VENCIDO" -> 0;
            case "HOY"     -> 1;
            case "PROXIMO" -> 2;
            default        -> 3;
        };
    }
}
```

---

### 7.5 Transacción: Registrar Pago

```java
// PagoService.java
public Mono<FinanzasActionResponse> registrarPago(RegistrarPagoDto dto) {
    DocumentReference pagoRef = db
        .collection("facturas").document(dto.getFacturaId())
        .collection("pagos").document(dto.getPagoId());

    DocumentReference facturaRef = db
        .collection("facturas").document(dto.getFacturaId());

    return toMono(db.runTransaction(tx -> {

        // 1. Validar
        DocumentSnapshot pagoSnap = tx.get(pagoRef).get();
        if ("PAGADO".equals(pagoSnap.getString("estado"))) {
            return new FinanzasActionResponse("OK", "El pago ya estaba registrado");
        }

        // 2. Actualizar pago
        tx.update(pagoRef, Map.of(
            "estado",       "PAGADO",
            "fechaPago",    dto.getFechaPago(),
            "metodoPago",   dto.getMetodoPago(),
            "actualizadoEn", Instant.now().toString()
        ));

        // 3. Leer todos los pagos para recalcular estado de la factura
        QuerySnapshot pagosSnap = tx.get(
            db.collection("facturas").document(dto.getFacturaId()).collection("pagos")
        ).get();

        String nuevoEstadoFactura = calcularEstadoFactura(pagosSnap.getDocuments(), dto.getPagoId());

        // 4. Actualizar factura
        boolean tieneVencidos = "VENCIDO".equals(nuevoEstadoFactura);
        boolean alertaActiva  = !"PAGADO".equals(nuevoEstadoFactura);

        tx.update(facturaRef, Map.of(
            "estado",         nuevoEstadoFactura,
            "_tieneVencidos", tieneVencidos,
            "_alertaActiva",  alertaActiva,
            "actualizadoEn",  Instant.now().toString()
        ));

        return new FinanzasActionResponse("OK", "Pago registrado correctamente");
    }));
}

private String calcularEstadoFactura(List<DocumentSnapshot> pagos, String pagoIdActualizado) {
    long pagados  = pagos.stream().filter(p -> "PAGADO".equals(p.getString("estado"))).count();
    long vencidos = pagos.stream().filter(p -> "VENCIDO".equals(p.getString("estado"))).count();
    long proximos = pagos.stream().filter(p -> "PROXIMO_VENCER".equals(p.getString("estado"))).count();

    if (pagados == pagos.size()) return "PAGADO";
    if (vencidos > 0) return "VENCIDO";
    if (proximos > 0) return "PROXIMO_VENCER";
    return "PENDIENTE";
}
```

---

## 8. Scheduler de Recálculo de Estados

El scheduler debe ejecutarse **cada día a las 00:05** para actualizar los estados `VENCIDO` y `PROXIMO_VENCER` en todos los pagos y cuotas pendientes.

```java
// EstadosScheduler.java
@Component
public class EstadosScheduler {

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Lima")
    public void recalcularEstados() {
        recalcularPagosFactura()
            .then(recalcularCuotasCuenta())
            .then(recalcularKpis())
            .subscribe();
    }

    private Mono<Void> recalcularPagosFactura() {
        String hoy      = LocalDate.now().toString();
        String en3Dias  = LocalDate.now().plusDays(3).toString();

        // Marcar como VENCIDO
        Flux<WriteResult> vencidos = toFlux(
            db.collectionGroup("pagos")
              .whereEqualTo("estado", "PENDIENTE")
              .whereLessThan("fechaProgramada", hoy)
              .get()
        ).flatMap(doc -> toMono(
            doc.getReference().update("estado", "VENCIDO", "actualizadoEn", Instant.now().toString())
        ));

        // Marcar como PROXIMO_VENCER
        Flux<WriteResult> proximos = toFlux(
            db.collectionGroup("pagos")
              .whereEqualTo("estado", "PENDIENTE")
              .whereGreaterThanOrEqualTo("fechaProgramada", hoy)
              .whereLessThanOrEqualTo("fechaProgramada", en3Dias)
              .get()
        ).flatMap(doc -> toMono(
            doc.getReference().update("estado", "PROXIMO_VENCER", "actualizadoEn", Instant.now().toString())
        ));

        // Limpiar PROXIMO_VENCER que ya superó los 3 días
        Flux<WriteResult> limpiarProximos = toFlux(
            db.collectionGroup("pagos")
              .whereEqualTo("estado", "PROXIMO_VENCER")
              .whereGreaterThan("fechaProgramada", en3Dias)
              .get()
        ).flatMap(doc -> toMono(
            doc.getReference().update("estado", "PENDIENTE", "actualizadoEn", Instant.now().toString())
        ));

        return Flux.merge(vencidos, proximos, limpiarProximos)
            .then()
            .doOnSuccess(v -> log.info("[Scheduler] Pagos facturas recalculados"))
            .then(propagarEstadosAFacturas());
    }

    // Después de actualizar pagos, propagar estado a las facturas padre
    private Mono<Void> propagarEstadosAFacturas() {
        return toFlux(
            db.collection("facturas")
              .whereNotEqualTo("estado", "PAGADO")
              .get()
        ).flatMap(facturaDoc -> {
            String facturaId = facturaDoc.getId();
            return toFlux(
                db.collection("facturas").document(facturaId).collection("pagos").get()
            )
            .collectList()
            .flatMap(pagos -> {
                String nuevoEstado = calcularEstadoFactura(pagos, null);
                return toMono(db.collection("facturas").document(facturaId).update(
                    "estado", nuevoEstado,
                    "_alertaActiva", !"PAGADO".equals(nuevoEstado),
                    "_tieneVencidos", "VENCIDO".equals(nuevoEstado)
                ));
            });
        }).then();
    }
}
```

---

## 9. Estrategia de Counters y KPIs

El documento `/finanzas_kpis/current` se actualiza mediante **incrementos atómicos** usando `FieldValue.increment(n)` de Firestore, sin leer el documento primero (evita race conditions).

```java
// KpisService.java — actualizaciones atómicas
public Mono<Void> onPagoRegistrado(String facturaId, boolean eraElUltimoPago) {
    Map<String, Object> updates = new HashMap<>();

    // Un pago menos pendiente
    updates.put("pagosVencidos", FieldValue.increment(-1));

    // Si era el último pago → factura ya no cuenta como pendiente
    if (eraElUltimoPago) {
        updates.put("totalFacturasPendientes", FieldValue.increment(-1));
    }

    // Sumar a egresos del mes actual
    updates.put("egresosDelMes", FieldValue.increment(montoPagado));
    updates.put("ultimaActualizacion", Instant.now().toString());

    return toMono(db.collection("finanzas_kpis").document("current").update(updates)).then();
}

public Mono<Void> onCuentaCreada() {
    return toMono(db.collection("finanzas_kpis").document("current").update(
        "totalCuentasPendientes", FieldValue.increment(1),
        "ultimaActualizacion", Instant.now().toString()
    )).then();
}

// Recalculo completo cada noche (más confiable que solo incrementos)
public Mono<Void> recalcularKpisCompleto() {
    // Corre 4 queries en paralelo
    Mono<Long> factPendientes = toFlux(
        db.collection("facturas").whereNotEqualTo("estado", "PAGADO").get()
    ).count();

    Mono<Long> pagosVenc = toFlux(
        db.collectionGroup("pagos").whereEqualTo("estado", "VENCIDO").get()
    ).count();

    Mono<Long> comPendientes = toFlux(
        db.collection("comisiones").whereEqualTo("estado", "PENDIENTE").get()
    ).count();

    // ... más queries

    return Mono.zip(factPendientes, pagosVenc, comPendientes)
        .flatMap(tuple -> toMono(
            db.collection("finanzas_kpis").document("current").set(Map.of(
                "totalFacturasPendientes", tuple.getT1(),
                "pagosVencidos",           tuple.getT2(),
                "comisionesPendientes",    tuple.getT3(),
                "ultimaActualizacion",     Instant.now().toString()
            ))
        )).then();
}
```

---

## 10. Consideraciones de Costo y Performance

### Estimación de lecturas por endpoint

| Endpoint | Lecturas Firestore | Estrategia |
|---|---|---|
| GET /facturas (sin filtros) | ~50 facturas × 1 + 50×2 pagos = **150** | Limit 50, paginación |
| GET /facturas/{id} | 1 factura + 2 pagos = **3** | Siempre eficiente |
| GET /finanzas/dashboard | 1 KPI doc + ~20 pagos + ~10 cuotas = **≈31** | Counter doc optimiza |
| GET /finanzas/alertas | ~N pagos vencidos + ~M cuotas = **variable** | Index por estado |
| POST /pagos | 1+1 lectura + 3+1 escritura = **~6 ops** | Transacción |
| POST /cuentas-pagar | 1 escritura + N cuotas = **1+N ops** | WriteBatch |

### Reglas para mantener costos bajos

1. **Paginación obligatoria** en todos los listados: `limit(50).startAfter(cursor)`.
2. **No usar `!=` en índices compuestos** — Firestore lo convierte en 2 queries internas; usar campos booleanos auxiliares (`_alertaActiva`) cuando sea posible.
3. **Límite de operaciones por batch**: máximo 500. Para datos de seed/migración, dividir en batches de 400.
4. **Cache en Spring** (`@Cacheable`) para el catálogo de tiendas — cambia raramente.
5. **collectionGroup queries** requieren índices explícitos definidos — sin el índice la query falla con `FAILED_PRECONDITION`.
6. **No usar `array-contains` en arrays grandes** — para los pagos (máx 2 elementos) es aceptable pero innecesario dado el esquema con subcollection.
7. **Monitorear con Firebase Performance Monitoring** los endpoints de alerta, que son los más costosos.

### Estructura de IDs recomendada

```
facturas:    FAC-{YYYYMMDD}-{sequence}    → FAC-20260306-001
pagos:       {facturaId}-P{1|2}           → FAC-20260306-001-P1
cuentas:     CTA-{YYYYMMDD}-{sequence}    → CTA-20260306-001
cuotas:      {cuentaId}-C{numero}         → CTA-20260306-001-C1
comisiones:  COM-{tiendaId}-{YYYYMMDD}    → COM-T001-20260306
```

> Usar IDs legibles en lugar de UUIDs facilita la depuración y el soporte operativo.
