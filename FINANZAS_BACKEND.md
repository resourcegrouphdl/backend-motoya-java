# Módulo Finanzas — Guía de Implementación Backend

> **Base URL:** `{API_BASE}/api`
> **Autenticación:** Bearer Token (Firebase JWT) en header `Authorization`
> **Content-Type:** `application/json` (salvo endpoints multipart indicados)
> **Todos los IDs** son `string` (UUID o código legible, ej. `"FAC-001"`)
> **Todas las fechas** son `string` en formato ISO 8601: `"YYYY-MM-DD"` para fechas, `"YYYY-MM-DDTHH:mm:ss"` para datetime

---

## Índice

1. [Modelo de Negocio](#1-modelo-de-negocio)
2. [Enums y Catálogos](#2-enums-y-catálogos)
3. [Módulo Facturas de Tiendas](#3-módulo-facturas-de-tiendas)
4. [Módulo Pagos](#4-módulo-pagos)
5. [Módulo Cuentas por Pagar](#5-módulo-cuentas-por-pagar)
6. [Módulo Comisiones](#6-módulo-comisiones)
7. [Dashboard y Alertas](#7-dashboard-y-alertas)
8. [Reportes y Exportaciones](#8-reportes-y-exportaciones)
9. [Reglas de Negocio Transversales](#9-reglas-de-negocio-transversales)
10. [Esquemas de Entidades](#10-esquemas-de-entidades)

---

## 1. Modelo de Negocio

### Contexto general
La empresa **financia la compra de motos** a clientes. El flujo financiero involucra tres dominios distintos:

### Dominio 1 — Pagos a Tiendas (`/facturas` y `/pagos`)
Cuando un cliente compra una moto financiada:
1. La **tienda** emite una factura a la empresa.
2. La empresa paga esa factura en **2 cuotas fijas**:
   - **Pago 1 — INICIAL**: Equivalente al 20% del total. Debe pagarse **máximo 2 días hábiles** después de la firma del contrato.
   - **Pago 2 — SALDO**: El 80% restante. Fecha según condición pactada con la tienda: **15 o 30 días** desde la fecha de la factura.
3. Cada pago puede tener un comprobante adjunto (voucher PDF/imagen).

### Dominio 2 — Cuentas por Pagar (`/cuentas-pagar`)
Gastos operativos de la empresa: servicios (luz, agua, internet), proveedores de motos, créditos bancarios, honorarios profesionales.
- Pueden tener **1 cuota** (pago único) o **N cuotas mensuales** generadas automáticamente.
- Cada cuota tiene su propia fecha de vencimiento y estado.

### Dominio 3 — Comisiones de Vendedores (`/comisiones`)
- Los vendedores en las tiendas reciben comisión por cada venta financiada cerrada.
- Se liquidan **por período** (semana o quincena).
- El monto de comisión por venta es fijo (configurable por tienda).
- Estado: PENDIENTE → PAGADO.

---

## 2. Enums y Catálogos

### `EstadoPago` (facturas y comisiones)
| Valor           | Descripción                                          |
|-----------------|------------------------------------------------------|
| `PAGADO`        | Pago registrado y confirmado                         |
| `PENDIENTE`     | Aún no vencido y sin pagar                           |
| `PROXIMO_VENCER`| Vence en ≤ 3 días calendario                         |
| `VENCIDO`       | Fecha de vencimiento superada sin pago               |

> **Cálculo automático**: El backend debe recalcular `PROXIMO_VENCER` y `VENCIDO` en cada consulta o mediante un job nocturno que actualice los estados.
> **Regla**: Si `fechaProgramada < hoy` → `VENCIDO`. Si `0 ≤ (fechaProgramada - hoy) ≤ 3 días` → `PROXIMO_VENCER`.

### `EstadoCuenta` (cuentas por pagar)
| Valor      | Descripción                         |
|------------|-------------------------------------|
| `PENDIENTE`| Cuota sin pagar                     |
| `VENCIDO`  | Fecha de vencimiento superada       |
| `PAGADO`   | Cuota pagada                        |

### `TipoCuenta`
| Valor        | Descripción              |
|--------------|--------------------------|
| `SERVICIO`   | Agua, luz, internet, etc.|
| `PROVEEDOR`  | Proveedor de motos       |
| `CREDITO`    | Crédito bancario         |
| `HONORARIOS` | Servicios profesionales  |

### `TipoConceptoPago`
| Valor    | Descripción                     |
|----------|---------------------------------|
| `INICIAL`| Cuota inicial (20% del total)   |
| `SALDO`  | Saldo restante (80% del total)  |

### `MetodoPago`
| Valor          |
|----------------|
| `EFECTIVO`     |
| `TRANSFERENCIA`|
| `CHEQUE`       |

---

## 3. Módulo Facturas de Tiendas

### 3.1 Listar Facturas

```
GET /api/facturas
```

**Query params:**
| Param       | Tipo        | Requerido | Descripción                          |
|-------------|-------------|-----------|--------------------------------------|
| `tiendaId`  | string      | No        | Filtrar por tienda                   |
| `estado`    | EstadoPago  | No        | Filtrar por estado de la factura     |
| `fechaDesde`| YYYY-MM-DD  | No        | Fecha de factura desde               |
| `fechaHasta`| YYYY-MM-DD  | No        | Fecha de factura hasta               |

**Response `200 OK`:**
```json
[
  {
    "id": "FAC-001",
    "numero": "F001-00123",
    "tiendaId": "T-001",
    "tiendaNombre": "Tienda Lima Norte",
    "ventaId": "VTA-101",
    "clienteNombre": "Roberto Quispe Mamani",
    "motoModelo": "Bajaj Pulsar 150",
    "montoTotal": 8500.00,
    "fechaFactura": "2026-02-12",
    "condicionPago": 15,
    "estado": "VENCIDO",
    "pagos": [
      {
        "id": "FAC-001-P1",
        "facturaId": "FAC-001",
        "numero": 1,
        "concepto": "INICIAL",
        "monto": 1700.00,
        "fechaProgramada": "2026-02-14",
        "fechaPago": "2026-02-13",
        "estado": "PAGADO",
        "voucherUrl": "https://storage.googleapis.com/.../voucher.pdf",
        "metodoPago": "TRANSFERENCIA"
      },
      {
        "id": "FAC-001-P2",
        "facturaId": "FAC-001",
        "numero": 2,
        "concepto": "SALDO",
        "monto": 6800.00,
        "fechaProgramada": "2026-02-27",
        "fechaPago": null,
        "estado": "VENCIDO",
        "voucherUrl": null,
        "metodoPago": null
      }
    ]
  }
]
```

> **Notas de implementación:**
> - El campo `estado` de la factura es **derivado**: se calcula del peor estado de sus `pagos[]`.
>   - Si todos los pagos están en `PAGADO` → `PAGADO`
>   - Si algún pago está `VENCIDO` → `VENCIDO`
>   - Si algún pago está `PROXIMO_VENCER` → `PROXIMO_VENCER`
>   - En otro caso → `PENDIENTE`
> - El array `pagos` debe incluirse siempre en la respuesta de lista (es necesario para renderizar los 2 chips de estado en la tabla).
> - Ordenar por `fechaFactura DESC`.

---

### 3.2 Obtener Factura por ID

```
GET /api/facturas/{id}
```

**Response `200 OK`:** mismo schema que un elemento de la lista (ver 3.1).
**Response `404 Not Found`:**
```json
{ "status": "ERROR", "message": "Factura no encontrada" }
```

---

### 3.3 Obtener Cronograma de Pagos

```
GET /api/facturas/{id}/cronograma
```

**Response `200 OK`:** array de `PagoFactura[]` (igual al campo `pagos` de la factura).

> Endpoint separado para carga diferida en la página de detalle. Puede devolver el mismo array que `pagos` del GET de factura.

---

## 4. Módulo Pagos

### 4.1 Registrar Pago

```
POST /api/pagos
Content-Type: application/json
```

**Request body:**
```json
{
  "facturaId": "FAC-001",
  "pagoId": "FAC-001-P2",
  "monto": 6800.00,
  "fechaPago": "2026-03-06",
  "metodoPago": "TRANSFERENCIA"
}
```

| Campo       | Tipo       | Requerido | Validación                                  |
|-------------|------------|-----------|---------------------------------------------|
| `facturaId` | string     | Sí        | Factura debe existir                        |
| `pagoId`    | string     | Sí        | Pago debe existir y pertenecer a la factura |
| `monto`     | number     | Sí        | `> 0`                                       |
| `fechaPago` | YYYY-MM-DD | Sí        | `≤ hoy`                                     |
| `metodoPago`| string     | Sí        | Uno de: `EFECTIVO`, `TRANSFERENCIA`, `CHEQUE`|

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Pago registrado correctamente" }
```

**Response `400 Bad Request`:**
```json
{ "status": "ERROR", "message": "El pago ya está registrado como PAGADO" }
```

> **Efectos secundarios:**
> - Actualizar `PagoFactura.estado` → `PAGADO`
> - Actualizar `PagoFactura.fechaPago` con la fecha recibida
> - Actualizar `PagoFactura.metodoPago`
> - Recalcular y actualizar `FacturaTienda.estado` (derivado de sus pagos)

---

### 4.2 Subir Comprobante de Pago

```
POST /api/pagos/{pagoId}/voucher
Content-Type: multipart/form-data
```

**Form fields:**
| Campo     | Tipo | Requerido | Descripción                           |
|-----------|------|-----------|---------------------------------------|
| `voucher` | File | Sí        | PDF o imagen (JPG/PNG). Máx 5 MB.     |

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Comprobante adjuntado correctamente" }
```

> **Efectos secundarios:**
> - Subir el archivo a Cloud Storage (o S3).
> - Actualizar `PagoFactura.voucherUrl` con la URL pública o signed URL del archivo.
> - El pago no necesita estar en estado `PAGADO` para adjuntar comprobante (puede cargarse antes de confirmar el pago).

---

## 5. Módulo Cuentas por Pagar

### 5.1 Listar Cuentas

```
GET /api/cuentas-pagar
```

**Query params:**
| Param    | Tipo        | Requerido | Descripción          |
|----------|-------------|-----------|----------------------|
| `tipo`   | TipoCuenta  | No        | Filtrar por tipo     |
| `estado` | EstadoCuenta| No        | Filtrar por estado   |

**Response `200 OK`:**
```json
[
  {
    "id": "CTA-004",
    "tipo": "PROVEEDOR",
    "proveedor": "Bajaj Auto Perú SAC",
    "descripcion": "Lote 5 motos Bajaj Pulsar 150",
    "numeroDocumento": "F-2026-001",
    "montoTotal": 45000.00,
    "numeroCuotas": 3,
    "estado": "PENDIENTE",
    "fechaVencimiento": "2026-03-26",
    "creadoEn": "2026-02-04T10:00:00",
    "cuotas": [
      {
        "id": "CTA-004-C1",
        "numero": 1,
        "monto": 15000.00,
        "fechaVencimiento": "2026-02-24",
        "fechaPago": "2026-02-24",
        "estado": "PAGADO"
      },
      {
        "id": "CTA-004-C2",
        "numero": 2,
        "monto": 15000.00,
        "fechaVencimiento": "2026-03-26",
        "fechaPago": null,
        "estado": "PENDIENTE"
      },
      {
        "id": "CTA-004-C3",
        "numero": 3,
        "monto": 15000.00,
        "fechaVencimiento": "2026-04-25",
        "fechaPago": null,
        "estado": "PENDIENTE"
      }
    ]
  }
]
```

> **Notas de implementación:**
> - `fechaVencimiento` de la cuenta = fecha de vencimiento de la **próxima cuota pendiente**.
> - `estado` de la cuenta = `PAGADO` si todas las cuotas están pagadas; `VENCIDO` si alguna cuota está vencida; `PENDIENTE` en otro caso.
> - Incluir siempre el array `cuotas[]` para soportar la vista expandible.
> - Ordenar: primero `VENCIDO`, luego `PENDIENTE`, luego `PAGADO`. Dentro de cada grupo, por `fechaVencimiento ASC`.

---

### 5.2 Crear Cuenta por Pagar

```
POST /api/cuentas-pagar
Content-Type: application/json
```

**Request body:**
```json
{
  "tipo": "PROVEEDOR",
  "proveedor": "Bajaj Auto Perú SAC",
  "descripcion": "Lote 5 motos Bajaj Pulsar 150",
  "numeroDocumento": "F-2026-001",
  "montoTotal": 45000.00,
  "numeroCuotas": 3,
  "fechaVencimiento": "2026-03-26"
}
```

| Campo             | Tipo        | Requerido | Validación                            |
|-------------------|-------------|-----------|---------------------------------------|
| `tipo`            | TipoCuenta  | Sí        |                                       |
| `proveedor`       | string      | Sí        | max 150 chars                         |
| `descripcion`     | string      | Sí        | max 250 chars                         |
| `numeroDocumento` | string      | Sí        | max 50 chars                          |
| `montoTotal`      | number      | Sí        | `> 0`                                 |
| `numeroCuotas`    | integer     | Sí        | `1 ≤ n ≤ 36`                          |
| `fechaVencimiento`| YYYY-MM-DD  | Sí        | Fecha de 1ra cuota. `≥ hoy`           |

**Response `201 Created`:** objeto `CuentaPorPagar` completo con cuotas generadas.

> **Generación automática de cuotas:**
> - Si `numeroCuotas = 1`: una sola cuota con `monto = montoTotal` y `fechaVencimiento` recibida.
> - Si `numeroCuotas > 1`: dividir `montoTotal / numeroCuotas` (redondear al centavo). La cuota final absorbe el residuo del redondeo. Las fechas se generan sumando **1 mes calendario** por cada cuota a partir de `fechaVencimiento`.
>   - Cuota 1: `fechaVencimiento`
>   - Cuota 2: `fechaVencimiento + 1 mes`
>   - Cuota N: `fechaVencimiento + (N-1) meses`

---

### 5.3 Pagar Cuenta (pago único)

```
POST /api/cuentas-pagar/{id}/pagar
Content-Type: application/json
```

**Body:** `{}` (sin campos requeridos, se asume `fechaPago = hoy`)

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Cuenta marcada como pagada" }
```

> **Usar solo cuando `numeroCuotas = 1`.**
> Efectos: marca la única cuota y la cuenta como `PAGADO`, registra `fechaPago = hoy`.

---

### 5.4 Pagar Cuota Individual

```
POST /api/cuentas-pagar/{cuentaId}/pagar/cuotas/{cuotaId}
Content-Type: application/json
```

**Body:** `{}` (se asume `fechaPago = hoy`)

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Cuota pagada" }
```

> **Efectos secundarios:**
> - `CuotaCuenta.estado` → `PAGADO`, `fechaPago` = hoy.
> - Recalcular `CuentaPorPagar.estado`:
>   - Si todas las cuotas están `PAGADO` → `PAGADO`
>   - Si alguna cuota está `VENCIDO` → `VENCIDO`
>   - En otro caso → `PENDIENTE`
> - Actualizar `CuentaPorPagar.fechaVencimiento` a la fecha de la siguiente cuota pendiente.

---

## 6. Módulo Comisiones

### 6.1 Listar Comisiones

```
GET /api/comisiones
```

**Query params:**
| Param         | Tipo       | Requerido | Descripción                            |
|---------------|------------|-----------|----------------------------------------|
| `tiendaId`    | string     | No        | Filtrar por tienda                     |
| `fechaInicio` | YYYY-MM-DD | No        | Filtrar por `periodoInicio >= valor`   |
| `fechaFin`    | YYYY-MM-DD | No        | Filtrar por `periodoFin <= valor`      |

**Response `200 OK`:**
```json
[
  {
    "id": "COM-001",
    "vendedorId": "V-001",
    "vendedorNombre": "Carlos Quispe Mamani",
    "tiendaId": "T-001",
    "tiendaNombre": "Tienda Lima Norte",
    "periodoInicio": "2026-02-27",
    "periodoFin": "2026-03-05",
    "totalVentas": 4,
    "montoComision": 480.00,
    "estado": "PENDIENTE",
    "pagadoEn": null
  }
]
```

> Ordenar por `estado` (PENDIENTE primero), luego por `periodoFin DESC`.

---

### 6.2 Pagar Comisión

```
POST /api/comisiones/{id}/pagar
Content-Type: application/json
```

**Body:** `{}`

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Comisión pagada correctamente" }
```

> **Efectos:** `ComisionVendedor.estado` → `PAGADO`, `pagadoEn` = datetime actual.

---

### 6.3 Exportar Comisiones (Excel)

```
GET /api/comisiones/exportar
```

**Query params:** iguales a los de listado (tiendaId, fechaInicio, fechaFin).

**Response `200 OK`:**
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="comisiones-YYYY-MM-DD.xlsx"`
- Body: archivo binario `.xlsx`

> **Columnas del Excel:**
> `Vendedor | Tienda | Período Inicio | Período Fin | Total Ventas | Monto Comisión | Estado | Pagado En`

---

## 7. Dashboard y Alertas

### 7.1 Dashboard Financiero

```
GET /api/finanzas/dashboard
```

**Response `200 OK`:**
```json
{
  "totalFacturasPendientes": 6,
  "montoFacturasPendientes": 42300.00,
  "pagosTiendaHoy": 1,
  "pagosVencidos": 3,
  "egresosDelMes": 13369.00,
  "comisionesPendientes": 4,
  "proximosPagos": [
    {
      "id": "FAC-001-P2",
      "descripcion": "Tienda Lima Norte — Roberto Quispe Mamani",
      "monto": 6800.00,
      "fechaVencimiento": "2026-03-06",
      "estado": "HOY",
      "modulo": "FACTURAS",
      "ruta": "/finanzas/pagos-tiendas/FAC-001"
    }
  ],
  "alertas": [
    {
      "id": "f-FAC-001-P2",
      "tipo": "VENCIDO",
      "mensaje": "Tienda Surco — Carmen López Huanca",
      "monto": 7360.00,
      "referencia": "FAC-002",
      "modulo": "FACTURAS",
      "ruta": "/finanzas/pagos-tiendas/FAC-002"
    }
  ]
}
```

**Descripción de cada campo calculado:**

| Campo                    | Cálculo                                                                 |
|--------------------------|-------------------------------------------------------------------------|
| `totalFacturasPendientes`| Count de facturas con `estado != PAGADO`                               |
| `montoFacturasPendientes`| Suma de `monto` de todos los `PagoFactura` con `estado != PAGADO`      |
| `pagosTiendaHoy`         | Count de `PagoFactura` con `fechaProgramada = hoy` y `estado != PAGADO`|
| `pagosVencidos`          | Count de `PagoFactura` con `estado = VENCIDO`                          |
| `egresosDelMes`          | Suma de cuotas `PAGADO` en el mes actual (facturas + cuentas)          |
| `comisionesPendientes`   | Count de `ComisionVendedor` con `estado = PENDIENTE`                   |
| `proximosPagos`          | Pagos/cuotas que vencen en los próximos 7 días (ver reglas abajo)      |
| `alertas`                | Todos los pagos/cuotas `VENCIDO`, `HOY`, `PROXIMO` (ver 7.2)           |

**Reglas para `proximosPagos`:**
- Incluir `PagoFactura` con `estado != PAGADO` y `0 ≤ (fechaProgramada - hoy) ≤ 7 días`
- Incluir `CuotaCuenta` con `estado = PENDIENTE` y `0 ≤ (fechaVencimiento - hoy) ≤ 7 días`
- Ordenar por `fechaVencimiento ASC`
- Máximo 20 registros

---

### 7.2 Alertas Financieras

```
GET /api/finanzas/alertas
```

**Response `200 OK`:** array de `AlertaFinanciera[]`

```json
[
  {
    "id": "f-FAC-002-P2",
    "tipo": "VENCIDO",
    "mensaje": "Tienda Surco — Carmen López Huanca",
    "monto": 7360.00,
    "referencia": "FAC-002",
    "modulo": "FACTURAS",
    "ruta": "/finanzas/pagos-tiendas/FAC-002"
  },
  {
    "id": "c-CTA-001-C1",
    "tipo": "VENCIDO",
    "mensaje": "Enel Distribución Perú — Luz eléctrica — Sede Principal",
    "monto": 380.00,
    "referencia": "CTA-001",
    "modulo": "CUENTAS",
    "ruta": "/finanzas/cuentas-pagar"
  }
]
```

**Tipos de alerta y criterios:**

| `tipo`    | Criterio                                              |
|-----------|-------------------------------------------------------|
| `VENCIDO` | `fechaProgramada/fechaVencimiento < hoy`               |
| `HOY`     | `fechaProgramada/fechaVencimiento = hoy`               |
| `PROXIMO` | `0 < (fecha - hoy) ≤ 7 días`                          |

**Fuentes de alertas:**
- `PagoFactura` con `estado != PAGADO` que cumplan los criterios de fecha
- `CuotaCuenta` con `estado IN (PENDIENTE, VENCIDO)` que cumplan los criterios

**Ordenamiento:** `VENCIDO` → `HOY` → `PROXIMO`, dentro de cada grupo por monto DESC.

---

## 8. Reportes y Exportaciones

### 8.1 Reporte Pagos a Tiendas

```
GET /api/reportes/pagos-tiendas
```

**Query params:** `fechaDesde`, `fechaHasta`, `tiendaId`, `estado`, `formato` (`excel` | `pdf`)

**Response `200 OK`:**
- Excel: `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- PDF: `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="pagos-tiendas-YYYY-MM-DD.xlsx"`

**Columnas sugeridas:**
`Factura | Tienda | Cliente | Moto | Monto Total | Pago 1 Monto | Pago 1 Fecha | Pago 1 Estado | Pago 2 Monto | Pago 2 Fecha | Pago 2 Estado | Estado General`

---

### 8.2 Reporte Comisiones

```
GET /api/reportes/comisiones
```

**Query params:** `fechaInicio`, `fechaFin`, `tiendaId`, `formato`

Misma estructura de respuesta que 8.1 con columnas de comisiones.

---

### 8.3 Reporte Egresos del Mes

```
GET /api/reportes/egresos-mes
```

**Query params:** `fechaDesde`, `fechaHasta`, `formato`

Consolida egresos de pagos a tiendas + cuentas por pagar + comisiones pagadas en el período.

---

## 9. Reglas de Negocio Transversales

### 9.1 Recálculo de Estados
El backend **NO debe confiar en el estado enviado por el cliente**. Los estados `VENCIDO` y `PROXIMO_VENCER` deben recalcularse en cada lectura o mediante un **scheduler** que ejecute cada noche a las `00:05`:
```
Para cada PagoFactura donde estado != PAGADO:
  diff = fechaProgramada - hoy
  si diff < 0:      estado = VENCIDO
  si 0 <= diff <= 3: estado = PROXIMO_VENCER
  si diff > 3:      estado = PENDIENTE

Para cada CuotaCuenta donde estado != PAGADO:
  diff = fechaVencimiento - hoy
  si diff < 0: estado = VENCIDO
  si diff >= 0: estado = PENDIENTE

Recalcular estado de FacturaTienda desde sus pagos.
Recalcular estado de CuentaPorPagar desde sus cuotas.
```

### 9.2 Validaciones al Registrar Pago
- No se puede pagar un `PagoFactura` que ya esté en `PAGADO`.
- Para el **Pago 2 (SALDO)** de una factura: el **Pago 1 (INICIAL)** debe estar en `PAGADO` primero (flujo normal; se puede hacer configurable).
- `fechaPago` no puede ser futura (`> hoy`).

### 9.3 Generación de Pagos de Factura
Al crear una `FacturaTienda` (si el backend admite creación desde este panel):
- Generar automáticamente 2 objetos `PagoFactura`:
  - P1 `INICIAL`: `monto = round(montoTotal * 0.20)`, `fechaProgramada = fechaFactura + 2 días hábiles`
  - P2 `SALDO`:   `monto = montoTotal - P1.monto`,    `fechaProgramada = fechaFactura + condicionPago días`

### 9.4 Idempotencia
Los endpoints `POST /pagos`, `POST /cuentas-pagar/{id}/pagar` y `POST /comisiones/{id}/pagar` deben ser idempotentes: si el registro ya está en `PAGADO`, devolver `200 OK` con `"status": "OK"` sin error ni duplicado.

### 9.5 Respuesta de error estándar
```json
{ "status": "ERROR", "message": "Descripción del error" }
```

---

## 10. Esquemas de Entidades

### `FacturaTienda`
```
id             String  PK
numero         String  ej. "F001-00123"
tiendaId       String  FK → Tienda
tiendaNombre   String  (desnormalizado para lecturas)
ventaId        String  FK → Venta
clienteNombre  String  (desnormalizado)
motoModelo     String  (desnormalizado)
montoTotal     Decimal
fechaFactura   Date
condicionPago  Integer  15 | 30
estado         Enum(EstadoPago)  [calculado]
pagos          PagoFactura[]
```

### `PagoFactura`
```
id              String  PK
facturaId       String  FK → FacturaTienda
numero          Integer  1 | 2
concepto        Enum(TipoConceptoPago)
monto           Decimal
fechaProgramada Date
fechaPago       Date?
estado          Enum(EstadoPago)  [calculado]
voucherUrl      String?
metodoPago      Enum(MetodoPago)?
```

### `CuentaPorPagar`
```
id               String  PK
tipo             Enum(TipoCuenta)
proveedor        String
descripcion      String
numeroDocumento  String
montoTotal       Decimal
numeroCuotas     Integer
estado           Enum(EstadoCuenta)  [calculado]
fechaVencimiento Date  [= próxima cuota pendiente]
creadoEn         DateTime
cuotas           CuotaCuenta[]
```

### `CuotaCuenta`
```
id               String  PK
cuentaId         String  FK → CuentaPorPagar
numero           Integer
monto            Decimal
fechaVencimiento Date
fechaPago        Date?
estado           Enum(EstadoCuenta)  [calculado]
```

### `ComisionVendedor`
```
id              String  PK
vendedorId      String  FK → Vendedor/User
vendedorNombre  String  (desnormalizado)
tiendaId        String  FK → Tienda
tiendaNombre    String  (desnormalizado)
periodoInicio   Date
periodoFin      Date
totalVentas     Integer  (cantidad de ventas en el período)
montoComision   Decimal  (totalVentas × tarifa por venta)
estado          Enum(EstadoPago)
pagadoEn        DateTime?
```

### `AlertaFinanciera` (DTO — no persiste)
```
id          String   "f-{pagoId}" | "c-{cuotaId}"
tipo        Enum     VENCIDO | HOY | PROXIMO
mensaje     String   "{proveedor/tienda} — {descripción/cliente}"
monto       Decimal
referencia  String   ID de la entidad padre (facturaId | cuentaId)
modulo      Enum     FACTURAS | CUENTAS | COMISIONES
ruta        String   ruta frontend para navegación
```

---

## Resumen de Endpoints

| Método | Endpoint                                         | Descripción                          |
|--------|--------------------------------------------------|--------------------------------------|
| GET    | `/api/finanzas/dashboard`                        | KPIs y datos de dashboard            |
| GET    | `/api/finanzas/alertas`                          | Alertas VENCIDO/HOY/PROXIMO          |
| GET    | `/api/facturas`                                  | Listar facturas con filtros          |
| GET    | `/api/facturas/{id}`                             | Detalle de factura                   |
| GET    | `/api/facturas/{id}/cronograma`                  | Cronograma de pagos                  |
| POST   | `/api/pagos`                                     | Registrar pago                       |
| POST   | `/api/pagos/{pagoId}/voucher`                    | Adjuntar comprobante (multipart)     |
| GET    | `/api/cuentas-pagar`                             | Listar cuentas por pagar             |
| POST   | `/api/cuentas-pagar`                             | Crear cuenta + generar cuotas        |
| POST   | `/api/cuentas-pagar/{id}/pagar`                  | Pagar cuenta (1 cuota)               |
| POST   | `/api/cuentas-pagar/{cuentaId}/pagar/cuotas/{cuotaId}` | Pagar cuota individual        |
| GET    | `/api/comisiones`                                | Listar comisiones con filtros        |
| POST   | `/api/comisiones/{id}/pagar`                     | Pagar comisión                       |
| GET    | `/api/comisiones/exportar`                       | Exportar Excel de comisiones         |
| GET    | `/api/reportes/pagos-tiendas`                    | Reporte Excel/PDF pagos tiendas      |
| GET    | `/api/reportes/comisiones`                       | Reporte Excel/PDF comisiones         |
| GET    | `/api/reportes/egresos-mes`                      | Reporte Excel/PDF egresos            |
