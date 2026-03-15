# Módulo Cobranzas — Guía de Implementación Backend

> **Base URL:** `{API_BASE}/api/v1`
> **Autenticación:** Bearer Token (Firebase JWT) en header `Authorization`
> **Content-Type:** `application/json` (salvo endpoints multipart indicados)
> **Todos los IDs** son `string` (UUID o código legible, ej. `"CTR-1001"`)
> **Todas las fechas** son `string` en formato ISO 8601: `"YYYY-MM-DD"` para fechas, `"YYYY-MM-DDTHH:mm:ss"` para datetime

---

## Índice

1. [Modelo de Negocio](#1-modelo-de-negocio)
2. [Enums y Catálogos](#2-enums-y-catálogos)
3. [Dashboard de Cobranzas](#3-dashboard-de-cobranzas)
4. [Casos Activos](#4-casos-activos)
5. [Vista 360 del Cliente](#5-vista-360-del-cliente)
6. [Promesas de Pago](#6-promesas-de-pago)
7. [Vouchers](#7-vouchers)
8. [Trazabilidad — Eventos del Caso](#8-trazabilidad--eventos-del-caso)
9. [Movimientos de Deuda](#9-movimientos-de-deuda)
10. [WhatsApp](#10-whatsapp)
11. [Alertas Operativas](#11-alertas-operativas)
12. [Estrategias de Cobranza](#12-estrategias-de-cobranza)
13. [Comprobantes de Pago](#13-comprobantes-de-pago)
14. [Reglas de Negocio Transversales](#14-reglas-de-negocio-transversales)
15. [Esquemas de Entidades](#15-esquemas-de-entidades)

---

## 1. Modelo de Negocio

### Contexto general

La empresa **financia la compra de motos** a clientes peruanos. Cuando un cliente entra en mora, el módulo de cobranzas gestiona la recuperación de la deuda. El flujo central es:

1. El cliente deja de pagar → el backend detecta mora automáticamente → crea un **Caso Activo**.
2. El agente de cobranzas ve el caso en la **Bandeja** (`/cobranzas/casos`), priorizado por días de mora.
3. El agente contacta al cliente (llamada, WhatsApp, visita) y registra el resultado como un **Evento** (event sourcing).
4. Si el cliente promete pagar → se registra una **Promesa de Pago**.
5. El cliente sube o envía evidencia de pago → llega como un **Voucher** que el agente valida.
6. Al aprobar el voucher → se aplica el pago al cronograma y se emite un **Comprobante** (Boleta/Factura).
7. Jobs nocturnos detectan incumplimientos, escalaciones y SLA vencidos → generan **Alertas** automáticas.

### Prioridad de casos

| Nivel | Criterio | Color UI |
|-------|----------|----------|
| `ALTA`  | >30 días mora O promesa incumplida O voucher discrepante | Rojo `#c62828` |
| `MEDIA` | 15–30 días mora | Naranja `#e65100` |
| `BAJA`  | 1–14 días mora | Verde `#2e7d32` |

La prioridad debe calcularse **dinámicamente** en el backend en cada consulta (no almacenarse como campo mutable).

---

## 2. Enums y Catálogos

### `PrioridadCaso`
`ALTA` | `MEDIA` | `BAJA`

### `EstadoCaso`
| Valor | Descripción |
|-------|-------------|
| `INTERVENCION_REQUERIDA` | +30 días sin acuerdo — requiere acción urgente |
| `PROMESA_VIGENTE`        | Hay promesa activa y dentro de plazo |
| `PROMESA_VENCE_HOY`      | Promesa vence en la fecha de hoy |
| `PROMESA_INCUMPLIDA`     | Promesa vencida sin pago detectado |
| `EN_SEGUIMIENTO`         | Caso activo sin promesa, en gestión normal |

### `NivelEstrategia`
| Valor | Días mora | Descripción |
|-------|-----------|-------------|
| `MORA_TEMPRANA` | 1–15 | Recordatorio amigable |
| `MORA_MEDIA`    | 16–30 | Presión de pago |
| `MORA_CRITICA`  | 31–60 | Gestión intensiva |
| `JUDICIAL`      | +60   | Derivación legal |

### `EstadoPromesa`
`VIGENTE` | `CUMPLIDA` | `INCUMPLIDA` | `CANCELADA`

### `EstadoVoucher`
`PENDIENTE` | `APROBADO` | `RECHAZADO`

### `EstadoComprobante`
`EMITIDO` | `ANULADO` | `PENDIENTE` | `ERROR_SUNAT`

### `NivelAlerta`
`INFO` | `WARNING` | `CRITICAL`

### `CanalComunicacion`
`WHATSAPP` | `SMS` | `LLAMADA` | `EMAIL` | `VISITA`

### `TipoMovimiento`
| Valor | Signo | Descripción |
|-------|-------|-------------|
| `SALDO_INICIAL`  | `+` | Deuda al aperturar el caso |
| `PAGO_CUOTA`     | `-` | Pago de cuota normal |
| `PAGO_PARCIAL`   | `-` | Abono parcial |
| `CARGO_MORA`     | `+` | Interés moratorio |
| `CARGO_COBRANZA` | `+` | Gastos de cobranza (visita, carta notarial) |
| `CONDONACION`    | `-` | Quita / condonación aprobada |
| `AJUSTE_ADMIN`   | `+/-` | Corrección administrativa |
| `REFINANCIAMIENTO` | `+` | Nuevo capital tras acuerdo |

---

## 3. Dashboard de Cobranzas

### 3.1 Obtener KPIs del Dashboard

```
GET /api/v1/cobranzas/dashboard
```

**Response `200 OK`:**
```json
{
  "promesasVencenHoy":      3,
  "promesasIncumplidas":    8,
  "vouchersPendientes":     2,
  "casosCriticos":         15,
  "moraTotal":         125400.00,
  "recuperacionMes":    38750.00,
  "porcentajeAutomatizado": 62
}
```

**Reglas de cálculo:**
- `promesasVencenHoy`: COUNT de promesas con `estado = VIGENTE` y `fechaPromesa = hoy`.
- `promesasIncumplidas`: COUNT de promesas con `estado = INCUMPLIDA` (no descartadas).
- `vouchersPendientes`: COUNT de vouchers con `estado = PENDIENTE`.
- `casosCriticos`: COUNT de casos con `diasMora >= 30` y sin acuerdo vigente.
- `moraTotal`: SUM del saldo actual de todos los casos con `diasMora > 0`.
- `recuperacionMes`: SUM de pagos aplicados en el mes calendario actual.
- `porcentajeAutomatizado`: `(mensajes_automaticos / total_mensajes_enviados) * 100` en el mes actual.

---

## 4. Casos Activos

### 4.1 Listar Casos Activos

```
GET /api/v1/cobranzas/casos
```

**Query params:**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `prioridad` | `ALTA \| MEDIA \| BAJA` | Filtrar por prioridad |
| `estado` | `EstadoCaso` | Filtrar por estado del caso |
| `busqueda` | string | Buscar por nombre de cliente o `contratoId` |
| `page` | int | Paginación (default 0) |
| `size` | int | Tamaño de página (default 50) |

**Response `200 OK`:**
```json
[
  {
    "contratoId":    "CTR-1001",
    "cliente":       "Juan Pérez Quispe",
    "diasMora":      35,
    "deuda":         2450.00,
    "ultimaAccion":  "Llamada - No contesta",
    "proximaAccion": "Llamar mañana 9am",
    "prioridad":     "ALTA",
    "estado":        "INTERVENCION_REQUERIDA",
    "telefono":      "+51987654321"
  }
]
```

**Reglas:**
- Ordenar por: `prioridad DESC` (ALTA → MEDIA → BAJA), luego `diasMora DESC`.
- `prioridad` debe calcularse dinámicamente (no almacenarse).
- `ultimaAccion` y `proximaAccion` vienen del último evento registrado en el historial del caso.
- Solo devolver casos con `diasMora > 0` y no cerrados.

### 4.2 Obtener Top Casos Urgentes (para Dashboard)

```
GET /api/v1/cobranzas/casos/urgentes?limit=4
```

Devuelve los N casos de prioridad ALTA con mayor días de mora. Misma estructura que 4.1.

---

## 5. Vista 360 del Cliente

Pantalla central de gestión. Consolida toda la información del caso en una sola respuesta.

### 5.1 Obtener Vista 360

```
GET /api/v1/cobranzas/casos/{contratoId}/vista360
```

**Response `200 OK`:**
```json
{
  "cliente": {
    "nombre":          "Juan Pérez Quispe",
    "telefono":        "+51987654321",
    "moto":            "Bajaj Pulsar 150 - Placa ABC-123",
    "saldoTotal":      2450.00,
    "diasMora":        35,
    "nivelEstrategia": "MORA_CRITICA"
  },
  "cronograma": [
    {
      "cuota":            1,
      "estado":           "PAGADA",
      "fechaVencimiento": "2025-09-15",
      "monto":            320.00,
      "fechaPago":        "2025-09-14"
    },
    {
      "cuota":            2,
      "estado":           "VENCIDA",
      "fechaVencimiento": "2025-10-15",
      "monto":            320.00
    }
  ],
  "historial": [
    {
      "tipo":      "LLAMADA",
      "mensaje":   "Cliente no contesta, 3er intento",
      "fecha":     "2026-01-10T09:32:00",
      "resultado": "NO_CONTESTA",
      "usuario":   "Ana García"
    }
  ],
  "promesas": [
    {
      "id":            "PRM-001",
      "fecha":         "2026-01-20",
      "monto":         640.00,
      "estado":        "INCUMPLIDA",
      "fechaRegistro": "2026-01-10T10:00:00",
      "observaciones": "Prometió pagar las 2 cuotas vencidas"
    }
  ]
}
```

**Notas:**
- `cronograma`: todas las cuotas del contrato, ordenadas por número de cuota.
- `historial`: últimos 50 eventos del caso, ordenados por `creadoEn DESC`.
- `promesas`: todas las promesas del contrato (incluidas cumplidas/canceladas), ordenadas por `fechaRegistro DESC`.

### 5.2 Asignar Agente al Caso

```
POST /api/v1/cobranzas/casos/{contratoId}/asignar-agente
```

**Request body:**
```json
{
  "agenteId":     "USR-005",
  "agenteNombre": "Ana García",
  "motivo":       "Reasignación por carga de trabajo"
}
```

**Response `200 OK`:**
```json
{
  "status":  "OK",
  "message": "Caso asignado a Ana García"
}
```

El backend debe generar un evento `CASO_ASIGNADO` en la trazabilidad del caso.

---

## 6. Promesas de Pago

### 6.1 Registrar Promesa de Pago

```
POST /api/v1/cobranzas/casos/{contratoId}/promesas
```

**Request body:**
```json
{
  "fechaPromesa":   "2026-02-01",
  "monto":          640.00,
  "observaciones":  "El cliente promete pagar las 2 cuotas vencidas el viernes"
}
```

**Response `201 Created`:**
```json
{
  "id":            "PRM-002",
  "fecha":         "2026-02-01",
  "monto":         640.00,
  "estado":        "VIGENTE",
  "fechaRegistro": "2026-01-15T14:22:00",
  "observaciones": "El cliente promete pagar las 2 cuotas vencidas el viernes"
}
```

**Reglas:**
- Sólo puede haber una promesa en estado `VIGENTE` por contrato a la vez. Si existe una vigente, debe cancelarse automáticamente antes de crear la nueva (o devolver error `409` para que el agente decida).
- El backend debe generar un evento `PROMESA_REGISTRADA` en la trazabilidad.
- Cambiar `estado` del caso a `PROMESA_VIGENTE`.

### 6.2 Listar Promesas del Caso

```
GET /api/v1/cobranzas/casos/{contratoId}/promesas
```

Devuelve el array de `PromesaPago[]` (misma estructura que en Vista 360).

### 6.3 Jobs automáticos para promesas (sin endpoint frontend)

El backend debe ejecutar estos jobs diariamente:

- **Job de vencimiento:** Si `fechaPromesa = hoy` y `estado = VIGENTE` → cambiar `estado` del caso a `PROMESA_VENCE_HOY` y generar alerta `PROMESA_VENCE_HOY`.
- **Job de incumplimiento:** Si `fechaPromesa < hoy` y `estado = VIGENTE` y no hay pago detectado → cambiar `estado` a `INCUMPLIDA` y generar alerta `PROMESA_INCUMPLIDA` + evento `PROMESA_INCUMPLIDA`.

---

## 7. Vouchers

Los vouchers son capturas de pago enviadas por el cliente (WhatsApp, foto, etc.) que el agente valida antes de aplicar el pago.

### 7.1 Listar Vouchers

```
GET /api/v1/cobranzas/vouchers
```

**Query params:**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `estado` | `PENDIENTE \| APROBADO \| RECHAZADO` | Filtrar por estado |
| `contratoId` | string | Filtrar por contrato |

**Response `200 OK`:**
```json
[
  {
    "id":              "VCH-001",
    "cliente":         "Juan Pérez Quispe",
    "contratoId":      "CTR-1001",
    "montoDetectado":  640.00,
    "montoEsperado":   640.00,
    "imagenUrl":       "https://storage.googleapis.com/.../voucher.jpg",
    "estado":          "PENDIENTE",
    "fechaDeteccion":  "2026-01-15T08:30:00"
  }
]
```

### 7.2 Aprobar Voucher

Al aprobar, el backend aplica el pago al cronograma y genera el comprobante.

```
POST /api/v1/cobranzas/vouchers/{voucherId}/aprobar
```

**Request body:**
```json
{
  "tipo":               "BOLETA",
  "emailReceptor":      "juan.perez@email.com",
  "rucReceptor":        null,
  "razonSocialReceptor": null
}
```

> Para `FACTURA`, incluir `rucReceptor` y `razonSocialReceptor`.

**Response `200 OK`:**
```json
{
  "status":        "OK",
  "message":       "Voucher aprobado. Boleta B001-00000024 generada.",
  "comprobanteId": "CPB-024",
  "saldoNuevo":    1810.00
}
```

**Secuencia que ejecuta el backend al aprobar:**
1. Validar que el voucher esté en `PENDIENTE`.
2. Aplicar el monto a las cuotas vencidas (más antigua primero).
3. Registrar `MovimientoDeuda` de tipo `PAGO_CUOTA` o `PAGO_PARCIAL`.
4. Cambiar `estado` del voucher a `APROBADO`.
5. Generar `ComprobantePago` (ver sección 13).
6. Enviar comprobante por email si `emailReceptor` está presente.
7. Si la promesa vigente del contrato queda satisfecha → cambiarla a `CUMPLIDA`.
8. Registrar eventos: `VOUCHER_APROBADO`, `PAGO_APLICADO`, `COMPROBANTE_GENERADO`.
9. Recalcular `diasMora`, `prioridad` y `estado` del caso.

### 7.3 Rechazar Voucher

```
POST /api/v1/cobranzas/vouchers/{voucherId}/rechazar
```

**Request body:**
```json
{
  "motivo":        "IMAGEN_ILEGIBLE",
  "observaciones": "La foto está borrosa, no se puede leer el monto"
}
```

**Valores de `motivo`:**
`MONTO_INCORRECTO` | `IMAGEN_ILEGIBLE` | `DUPLICADO` | `DATOS_NO_COINCIDEN` | `OTRO`

**Response `200 OK`:**
```json
{
  "status":  "OK",
  "message": "Voucher rechazado"
}
```

El backend debe registrar evento `VOUCHER_RECHAZADO` en la trazabilidad.

---

## 8. Trazabilidad — Eventos del Caso

Implementación de **Event Sourcing**. Cada acción sobre un caso genera un evento inmutable con payload tipado. Reemplaza el historial de texto libre.

### 8.1 Listar Eventos de un Caso

```
GET /api/v1/cobranzas/casos/{contratoId}/eventos
```

**Query params:**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `limit` | int | Máximo de eventos (default 50) |
| `offset` | int | Para paginación |

**Response `200 OK`:**
```json
[
  {
    "id":             "EVT-001",
    "contratoId":     "CTR-1001",
    "tipo":           "CONTACTO_FALLIDO",
    "payload": {
      "canal":      "LLAMADA",
      "motivo":     "NO_CONTESTA",
      "intentoNum": 3
    },
    "usuarioId":      "USR-005",
    "usuarioNombre":  "Ana García",
    "automatico":     false,
    "creadoEn":       "2026-01-10T09:32:00"
  }
]
```

### 8.2 Registrar Evento Manual

```
POST /api/v1/cobranzas/casos/{contratoId}/eventos
```

**Request body:**
```json
{
  "tipo":    "CONTACTO_EXITOSO",
  "payload": {
    "canal":       "LLAMADA",
    "duracionMin": 5,
    "resumen":     "Cliente acepta pagar el viernes 17/01"
  }
}
```

**Response `201 Created`:** Devuelve el `EventoCobranza` creado.

**Tipos de evento y sus payloads esperados:**

| `tipo` | Campos del `payload` |
|--------|----------------------|
| `CONTACTO_EXITOSO` | `canal`, `duracionMin?`, `resumen` |
| `CONTACTO_FALLIDO` | `canal`, `motivo` (`NO_CONTESTA\|NUMERO_EQUIVOCADO\|BUZON\|BLOQUEADO\|SIN_SEÑAL`), `intentoNum` |
| `EXCEPCION_REGISTRADA` | `tipo` (`FALLECIDO\|INSOLVENTE\|DISPUTA\|OPT_OUT\|JUDICIAL_ACTIVO`), `descripcion`, `documentoRef?`, `contactoBloqueado` |
| `CASO_CERRADO` | `motivo` (`PAGADO_TOTAL\|ACUERDO_CUMPLIDO\|JUDICIAL_TRANSFERIDO\|CASTIGADO\|CONDONADO`), `saldoFinal`, `resumen?` |
| `ACUERDO_REGISTRADO` | `acuerdoId`, `tipo` (`REFINANCIAMIENTO\|PLAN_PAGOS\|QUITA`), `numeroCuotas`, `montoCuota`, `fechaInicio`, `montoTotalAcordado` |

> **Nota:** Los eventos `PROMESA_*`, `VOUCHER_*`, `PAGO_APLICADO`, `COMPROBANTE_GENERADO`, `ESTRATEGIA_*` son generados **automáticamente** por el backend al ejecutar las operaciones correspondientes — no requieren endpoint propio.

---

## 9. Movimientos de Deuda

Trazabilidad de saldo del contrato (ledger de movimientos). El saldo real es la suma de todos los movimientos.

### 9.1 Listar Movimientos de un Caso

```
GET /api/v1/cobranzas/casos/{contratoId}/movimientos
```

**Response `200 OK`:**
```json
{
  "resumen": {
    "saldoActual":     2450.00,
    "capitalOriginal": 9600.00,
    "totalPagado":     7150.00,
    "totalMora":         120.00,
    "totalCondonado":      0.00,
    "ultimoMovimiento": "2026-01-10T08:00:00"
  },
  "movimientos": [
    {
      "id":            "MOV-001",
      "contratoId":    "CTR-1001",
      "tipo":          "SALDO_INICIAL",
      "monto":         9600.00,
      "saldoAnterior": 0.00,
      "saldoNuevo":    9600.00,
      "descripcion":   "Deuda inicial al aperturar caso de cobranza",
      "autorizadoPor": "SISTEMA",
      "creadoEn":      "2025-06-01T00:00:00"
    },
    {
      "id":            "MOV-012",
      "contratoId":    "CTR-1001",
      "tipo":          "PAGO_CUOTA",
      "monto":         -320.00,
      "saldoAnterior": 2770.00,
      "saldoNuevo":    2450.00,
      "descripcion":   "Pago cuota N°3 aprobado. Voucher VCH-001",
      "voucherId":     "VCH-001",
      "comprobanteId": "CPB-023",
      "cuotaNumero":   3,
      "autorizadoPor": "USR-005",
      "creadoEn":      "2026-01-10T08:00:00"
    }
  ]
}
```

**Reglas:**
- `monto` positivo = cargo (aumenta deuda). Negativo = abono (reduce deuda).
- `saldoNuevo = saldoAnterior + monto` siempre.
- El primer movimiento de un caso siempre es `SALDO_INICIAL` creado por el sistema.
- Ordenar por `creadoEn DESC`.

---

## 10. WhatsApp

El backend actúa como intermediario con la **WhatsApp Business API (Meta)**. El frontend solo selecciona plantilla, previsualiza y confirma el envío.

### 10.1 Listar Plantillas Disponibles

```
GET /api/v1/cobranzas/whatsapp/plantillas
```

**Query params:**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `nivel` | `NivelEstrategia` | Filtrar por nivel de mora |
| `categoria` | `CategoriaPlantilla` | Filtrar por categoría |

**Response `200 OK`:**
```json
[
  {
    "id":               "PLT-001",
    "nombre":           "Recordatorio pago mora temprana",
    "categoria":        "RECORDATORIO_PAGO",
    "nivelMora":        "MORA_TEMPRANA",
    "cuerpo":           "Hola {{nombre_cliente}}, te recordamos que tienes una cuota vencida de S/ {{monto_deuda}}. Puedes regularizar hasta el {{fecha_limite}}. Motoya Financia.",
    "variables": [
      { "nombre": "nombre_cliente", "descripcion": "Nombre completo del cliente", "valorEjemplo": "Juan Pérez" },
      { "nombre": "monto_deuda",    "descripcion": "Monto total en mora (S/)",    "valorEjemplo": "320.00" },
      { "nombre": "fecha_limite",   "descripcion": "Fecha límite de pago",        "valorEjemplo": "20/01/2026" }
    ],
    "activa":            true,
    "aprobadaPorMeta":   true
  }
]
```

**Categorías de plantilla:** `RECORDATORIO_PAGO` | `PROMESA_CONFIRMACION` | `VOUCHER_CONFIRMACION` | `MORA_TEMPRANA` | `MORA_CRITICA` | `JUDICIAL_AVISO` | `ACUERDO_PAGO`

> **Importante:** Las plantillas deben estar aprobadas por Meta antes de usarse. El campo `aprobadaPorMeta` indica si están disponibles para envío.

### 10.2 Previsualizar Mensaje

```
POST /api/v1/cobranzas/whatsapp/preview
```

**Request body:**
```json
{
  "contratoId":   "CTR-1001",
  "plantillaId":  "PLT-001",
  "variablesValores": {
    "nombre_cliente": "Juan Pérez",
    "monto_deuda":    "640.00",
    "fecha_limite":   "20/01/2026"
  }
}
```

**Response `200 OK`:**
```json
{
  "mensajePreview": "Hola Juan Pérez, te recordamos que tienes una cuota vencida de S/ 640.00. Puedes regularizar hasta el 20/01/2026. Motoya Financia.",
  "telefono":       "+51987654321",
  "clienteNombre":  "Juan Pérez Quispe"
}
```

El backend sustituye las variables en la plantilla y devuelve el texto final. No envía nada.

### 10.3 Enviar Mensaje WhatsApp

```
POST /api/v1/cobranzas/whatsapp/enviar
```

**Request body:**
```json
{
  "contratoId":    "CTR-1001",
  "plantillaId":   "PLT-001",
  "variablesValores": {
    "nombre_cliente": "Juan Pérez",
    "monto_deuda":    "640.00",
    "fecha_limite":   "20/01/2026"
  },
  "telefonoDestino": "+51987654321"
}
```

> `telefonoDestino` es opcional. Si no se envía, usar el teléfono del caso.

**Response `200 OK`:**
```json
{
  "status":    "OK",
  "mensajeId": "MSG-045",
  "wamid":     "wamid.ID...",
  "message":   "Mensaje enviado correctamente"
}
```

**Secuencia backend:**
1. Resolver el teléfono del destinatario.
2. Llamar a Meta WhatsApp Business API con la plantilla y variables.
3. Guardar el `MensajeWhatsApp` con `estado = ENVIADO` y el `wamid` devuelto por Meta.
4. Registrar evento `MENSAJE_WHATSAPP` en la trazabilidad del caso.

### 10.4 Historial de Mensajes de un Caso

```
GET /api/v1/cobranzas/casos/{contratoId}/whatsapp
```

**Response `200 OK`:**
```json
[
  {
    "id":              "MSG-045",
    "contratoId":      "CTR-1001",
    "clienteNombre":   "Juan Pérez Quispe",
    "telefono":        "+51987654321",
    "plantillaId":     "PLT-001",
    "plantillaNombre": "Recordatorio pago mora temprana",
    "mensajeReal":     "Hola Juan Pérez...",
    "estado":          "LEIDO",
    "wamid":           "wamid.ID...",
    "enviadoEn":       "2026-01-15T10:30:00",
    "entregadoEn":     "2026-01-15T10:30:05",
    "leidoEn":         "2026-01-15T10:45:00",
    "automatico":      false,
    "enviadoPor":      "USR-005"
  }
]
```

### Webhook Meta → Backend (actualización de estado de mensajes)

Meta notifica cambios de estado (`ENTREGADO`, `LEIDO`, `FALLIDO`) vía webhook. El backend debe:
1. Recibir el webhook en `POST /webhooks/whatsapp`.
2. Buscar el `MensajeWhatsApp` por `wamid`.
3. Actualizar `estado`, `entregadoEn` o `leidoEn` según corresponda.

---

## 11. Alertas Operativas

Alertas generadas por jobs automáticos del backend. El agente las ve en el panel central.

### 11.1 Listar Alertas Activas

```
GET /api/v1/cobranzas/alertas
```

Devuelve alertas no descartadas (`descartada = false`), ordenadas por nivel (`CRITICAL` > `WARNING` > `INFO`) y luego por `creadoEn DESC`.

**Query params:**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `nivel` | `INFO \| WARNING \| CRITICAL` | Filtrar por nivel |
| `leida` | boolean | Filtrar por estado de lectura |

**Response `200 OK`:**
```json
{
  "resumen": {
    "totalCriticas": 2,
    "totalWarnings": 5,
    "totalInfo":     1,
    "totalNoLeidas": 6
  },
  "alertas": [
    {
      "id":             "ALT-001",
      "tipo":           "PROMESA_INCUMPLIDA",
      "nivel":          "CRITICAL",
      "titulo":         "Promesa incumplida",
      "descripcion":    "Juan Pérez (CTR-1001) no pagó su promesa de S/ 640 del 20/01",
      "contratoId":     "CTR-1001",
      "clienteNombre":  "Juan Pérez Quispe",
      "accionSugerida": "Contactar al cliente para renegociar",
      "accionRuta":     "/cobranzas/vista360/CTR-1001",
      "leida":          false,
      "descartada":     false,
      "creadoEn":       "2026-01-21T00:05:00",
      "expiraEn":       null
    }
  ]
}
```

### 11.2 Marcar Alerta como Leída

```
PATCH /api/v1/cobranzas/alertas/{alertaId}/leer
```

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Alerta marcada como leída" }
```

### 11.3 Marcar Todas las Alertas como Leídas

```
POST /api/v1/cobranzas/alertas/marcar-todas-leidas
```

**Response `200 OK`:**
```json
{ "status": "OK", "message": "8 alertas marcadas como leídas" }
```

### 11.4 Descartar Alerta

```
DELETE /api/v1/cobranzas/alertas/{alertaId}
```

> Soft delete — marcar `descartada = true`. No eliminar de la base de datos.

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Alerta descartada" }
```

### Tipos de alerta que el backend debe generar automáticamente

| `tipo` | `nivel` | Cuándo generarla |
|--------|---------|------------------|
| `PROMESA_VENCE_HOY` | `CRITICAL` | Job diario: promesa `VIGENTE` con `fechaPromesa = hoy` |
| `PROMESA_VENCE_MAÑANA` | `WARNING` | Job diario: promesa `VIGENTE` con `fechaPromesa = mañana` |
| `PROMESA_INCUMPLIDA` | `CRITICAL` | Job diario: promesa `VIGENTE` con `fechaPromesa < hoy` y sin pago |
| `CASO_SIN_GESTION` | `WARNING` | Job: caso activo sin eventos en los últimos 3 días |
| `ESCALACION_REQUERIDA` | `WARNING` | Job: `diasMora` supera el umbral del nivel actual |
| `CASO_CRITICO_SIN_AGENTE` | `CRITICAL` | Job: caso con `diasMora >= 30` sin agente asignado |
| `VOUCHER_PENDIENTE` | `INFO` | Al recibir un voucher nuevo |
| `VOUCHER_MONTO_DISCREPANTE` | `WARNING` | Al recibir voucher con `montoDetectado != montoEsperado` |
| `ESTRATEGIA_FALLIDA_MULTIPLE` | `WARNING` | Si una estrategia falla 3+ veces seguidas para el mismo contrato |
| `SLA_VENCE_HOY` | `WARNING` | Job: SLA de gestión vence hoy |
| `SLA_VENCIDO` | `CRITICAL` | Job: SLA superado sin cierre |
| `PAGO_NO_CONCILIADO` | `INFO` | Pago detectado en cuenta bancaria sin voucher asociado |

**Regla anti-duplicados:** No generar la misma alerta si ya existe una activa (`descartada = false`) del mismo `tipo` + `contratoId`.

---

## 12. Estrategias de Cobranza

Configuración de automatización: qué canal usar, qué mensaje enviar y cuándo, según el nivel de mora del caso.

### 12.1 Listar Estrategias

```
GET /api/v1/cobranzas/estrategias
```

**Response `200 OK`:**
```json
[
  {
    "id":             "EST-001",
    "nivel":          "MORA_TEMPRANA",
    "canal":          "WHATSAPP",
    "mensaje":        "Hola {{nombre}}, recuerda que tienes una cuota vencida de S/ {{monto}}.",
    "activo":         true,
    "diasMoraDesde":  1,
    "diasMoraHasta":  15,
    "frecuenciaDias": 3
  }
]
```

### 12.2 Crear Estrategia

```
POST /api/v1/cobranzas/estrategias
```

**Request body:**
```json
{
  "nivel":          "MORA_MEDIA",
  "canal":          "LLAMADA",
  "mensaje":        "Gestión de llamada presión media",
  "activo":         true,
  "diasMoraDesde":  16,
  "diasMoraHasta":  30,
  "frecuenciaDias": 2
}
```

**Response `201 Created`:** Devuelve la estrategia creada con `id`.

### 12.3 Actualizar Estrategia

```
PUT /api/v1/cobranzas/estrategias/{estrategiaId}
```

**Request body:** Misma estructura que el POST. Devuelve la estrategia actualizada.

### 12.4 Eliminar Estrategia

```
DELETE /api/v1/cobranzas/estrategias/{estrategiaId}
```

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Estrategia eliminada" }
```

### Motor de Automatización (job)

El backend debe ejecutar periódicamente (ej. cada hora o diariamente al amanecer):

1. Obtener todos los casos activos.
2. Para cada caso, determinar su `NivelEstrategia` según `diasMora`.
3. Buscar estrategias activas para ese nivel.
4. Verificar si se cumple `frecuenciaDias` desde el último disparo.
5. Si aplica → enviar mensaje por el canal configurado.
6. Registrar `DisparoEstrategia` y evento `ESTRATEGIA_DISPARADA`.

---

## 13. Comprobantes de Pago

Boletas y Facturas electrónicas SUNAT-compatibles (UBL 2.1), generadas automáticamente al aprobar un voucher.

### 13.1 Listar Comprobantes

```
GET /api/v1/cobranzas/comprobantes
```

**Query params:**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `tipo` | `BOLETA \| FACTURA` | Filtrar por tipo |
| `estado` | `EstadoComprobante` | Filtrar por estado |
| `contratoId` | string | Filtrar por contrato |
| `fechaDesde` | YYYY-MM-DD | Fecha de emisión desde |
| `fechaHasta` | YYYY-MM-DD | Fecha de emisión hasta |

**Response `200 OK`:**
```json
[
  {
    "id":             "CPB-024",
    "numeroCompleto": "B001-00000024",
    "tipo":           "BOLETA",
    "estado":         "EMITIDO",
    "clienteNombre":  "Juan Pérez Quispe",
    "contratoId":     "CTR-1001",
    "total":          640.00,
    "fechaEmision":   "2026-01-15",
    "pdfUrl":         "https://storage.googleapis.com/.../B001-00000024.pdf"
  }
]
```

### 13.2 Obtener Detalle de Comprobante

```
GET /api/v1/cobranzas/comprobantes/{comprobanteId}
```

**Response `200 OK`:**
```json
{
  "id":             "CPB-024",
  "serie":          "B001",
  "numero":         "00000024",
  "numeroCompleto": "B001-00000024",
  "tipo":           "BOLETA",
  "estado":         "EMITIDO",
  "contratoId":     "CTR-1001",
  "voucherId":      "VCH-001",
  "emisor": {
    "ruc":         "20603852748",
    "razonSocial": "MOTOYA FINANCIA SAC",
    "direccion":   "Av. Principal 123, Lima",
    "ubigeo":      "150101"
  },
  "receptor": {
    "tipoDocumento":   "DNI",
    "numeroDocumento": "12345678",
    "nombreCompleto":  "Juan Pérez Quispe",
    "direccion":       "Jr. Las Flores 456, Lima",
    "email":           "juan.perez@email.com"
  },
  "items": [
    {
      "descripcion":    "Cuota N°2 - Contrato CTR-1001",
      "cantidad":       1,
      "precioUnitario": 320.00,
      "totalItem":      320.00
    },
    {
      "descripcion":    "Cuota N°3 - Contrato CTR-1001",
      "cantidad":       1,
      "precioUnitario": 320.00,
      "totalItem":      320.00
    }
  ],
  "subTotal":     542.37,
  "igv":           97.63,
  "total":        640.00,
  "hashSunat":    "abc123...",
  "cdrSunat":     "cdr_data...",
  "qrData":       "20603852748|03|B001|00000024|97.63|640.00|2026-01-15|6|12345678|",
  "pdfUrl":       "https://storage.googleapis.com/.../B001-00000024.pdf",
  "xmlUrl":       "https://storage.googleapis.com/.../B001-00000024.xml",
  "fechaEmision": "2026-01-15",
  "creadoEn":     "2026-01-15T08:05:00"
}
```

### 13.3 Generar Comprobante Manualmente

Para casos donde no se usó el flujo de voucher (ej. pago en efectivo).

```
POST /api/v1/cobranzas/comprobantes/generar
```

**Request body:**
```json
{
  "voucherId":          "VCH-001",
  "tipo":               "BOLETA",
  "emailReceptor":      "juan.perez@email.com",
  "rucReceptor":        null,
  "razonSocialReceptor": null
}
```

**Response `201 Created`:** Devuelve el `ComprobantePago` completo.

### 13.4 Descargar PDF

```
GET /api/v1/cobranzas/comprobantes/{comprobanteId}/pdf
```

**Response:** Redirect 302 a `pdfUrl` en Firebase Storage (o `Content-Type: application/pdf` con stream).

### 13.5 Anular Comprobante

Solo se puede anular un comprobante `EMITIDO`. Requiere Nota de Crédito (NC) ante SUNAT.

```
POST /api/v1/cobranzas/comprobantes/{comprobanteId}/anular
```

**Request body:**
```json
{
  "comprobanteId": "CPB-024",
  "motivo":        "Error en monto detectado por el cliente"
}
```

**Response `200 OK`:**
```json
{
  "status":  "OK",
  "message": "Comprobante B001-00000024 anulado. Se generó NC B002-00000001."
}
```

---

## 14. Reglas de Negocio Transversales

### Cálculo de `diasMora`

```
diasMora = MAX(0, hoy - fechaVencimientoCuotaMasAntigua)
```
Calculado dinámicamente en cada consulta. No almacenar.

### Cálculo de `prioridad`

| Condición | Prioridad |
|-----------|-----------|
| `diasMora >= 30` OR `estado = PROMESA_INCUMPLIDA` OR voucher con discrepancia | `ALTA` |
| `15 <= diasMora < 30` | `MEDIA` |
| `1 <= diasMora < 15` | `BAJA` |

### Aplicación de pagos a cuotas

Cuando se aprueba un voucher:
1. Cubrir la cuota más antigua primero (`VENCIDA` primero, luego `PENDIENTE`).
2. Si el pago cubre más de una cuota → marcar todas las cubiertas como `PAGADA`.
3. Si el monto es parcial → registrar `PAGO_PARCIAL` y dejar la cuota en `VENCIDA` con saldo reducido.

### Escalación automática de nivel

Cuando `diasMora` supera el umbral del nivel actual del caso:
- Generar evento `NIVEL_ESCALADO`.
- Generar alerta `ESCALACION_REQUERIDA`.
- Actualizar `nivelEstrategia` del caso.

### Autenticación y autorización

- Todos los endpoints requieren Bearer Token Firebase JWT.
- Los endpoints de escritura (POST/PUT/DELETE) requieren rol `COBRANZAS_AGENTE` o superior.
- La anulación de comprobantes requiere rol `COBRANZAS_SUPERVISOR`.

---

## 15. Esquemas de Entidades

### `CasoActivo`
```json
{
  "contratoId":    "string — ID del contrato (clave primaria)",
  "cliente":       "string — nombre completo",
  "diasMora":      "int — calculado dinámicamente",
  "deuda":         "decimal — saldo actual",
  "ultimaAccion":  "string — resumen del último evento",
  "proximaAccion": "string — tarea pendiente del agente",
  "prioridad":     "ALTA | MEDIA | BAJA — calculado",
  "estado":        "EstadoCaso",
  "telefono":      "string? — con código de país +51..."
}
```

### `PromesaPago`
```json
{
  "id":            "string",
  "fecha":         "YYYY-MM-DD",
  "monto":         "decimal",
  "estado":        "VIGENTE | CUMPLIDA | INCUMPLIDA | CANCELADA",
  "fechaRegistro": "ISO timestamp",
  "observaciones": "string?"
}
```

### `Voucher`
```json
{
  "id":              "string",
  "cliente":         "string",
  "contratoId":      "string",
  "montoDetectado":  "decimal — monto que aparece en la imagen",
  "montoEsperado":   "decimal — monto que el sistema espera",
  "imagenUrl":       "string — URL pública Firebase Storage",
  "estado":          "PENDIENTE | APROBADO | RECHAZADO",
  "fechaDeteccion":  "ISO timestamp"
}
```

### `AlertaCobranza`
```json
{
  "id":             "string",
  "tipo":           "TipoAlerta",
  "nivel":          "INFO | WARNING | CRITICAL",
  "titulo":         "string — texto corto",
  "descripcion":    "string — detalle completo",
  "contratoId":     "string?",
  "clienteNombre":  "string?",
  "accionSugerida": "string? — texto de ayuda para el agente",
  "accionRuta":     "string? — ruta Angular para navegar",
  "leida":          "boolean",
  "descartada":     "boolean",
  "creadoEn":       "ISO timestamp",
  "expiraEn":       "ISO timestamp? — null = no expira"
}
```

### `EventoCobranza`
```json
{
  "id":            "string",
  "contratoId":    "string",
  "tipo":          "TipoEventoCobranza",
  "payload":       "object — ver tabla de payloads en sección 8",
  "usuarioId":     "string",
  "usuarioNombre": "string",
  "automatico":    "boolean — true si fue el sistema",
  "creadoEn":      "ISO timestamp"
}
```

### `MovimientoDeuda`
```json
{
  "id":             "string",
  "contratoId":     "string",
  "tipo":           "TipoMovimiento",
  "monto":          "decimal — positivo=cargo, negativo=abono",
  "saldoAnterior":  "decimal",
  "saldoNuevo":     "decimal",
  "descripcion":    "string",
  "voucherId":      "string?",
  "comprobanteId":  "string?",
  "cuotaNumero":    "int?",
  "acuerdoId":      "string?",
  "autorizadoPor":  "string — UID usuario o 'SISTEMA'",
  "creadoEn":       "ISO timestamp"
}
```

---

## Resumen de Endpoints

| # | Método | Ruta | Descripción |
|---|--------|------|-------------|
| 1 | GET | `/cobranzas/dashboard` | KPIs del dashboard |
| 2 | GET | `/cobranzas/casos` | Listar casos activos |
| 3 | GET | `/cobranzas/casos/urgentes` | Top N casos prioridad ALTA |
| 4 | GET | `/cobranzas/casos/{id}/vista360` | Vista 360 del caso |
| 5 | POST | `/cobranzas/casos/{id}/asignar-agente` | Asignar agente al caso |
| 6 | GET | `/cobranzas/casos/{id}/promesas` | Listar promesas |
| 7 | POST | `/cobranzas/casos/{id}/promesas` | Registrar promesa |
| 8 | GET | `/cobranzas/casos/{id}/eventos` | Listar eventos (trazabilidad) |
| 9 | POST | `/cobranzas/casos/{id}/eventos` | Registrar evento manual |
| 10 | GET | `/cobranzas/casos/{id}/movimientos` | Listar movimientos de deuda |
| 11 | GET | `/cobranzas/casos/{id}/whatsapp` | Historial WhatsApp del caso |
| 12 | GET | `/cobranzas/vouchers` | Listar vouchers |
| 13 | POST | `/cobranzas/vouchers/{id}/aprobar` | Aprobar voucher |
| 14 | POST | `/cobranzas/vouchers/{id}/rechazar` | Rechazar voucher |
| 15 | GET | `/cobranzas/whatsapp/plantillas` | Listar plantillas WhatsApp |
| 16 | POST | `/cobranzas/whatsapp/preview` | Previsualizar mensaje |
| 17 | POST | `/cobranzas/whatsapp/enviar` | Enviar mensaje WhatsApp |
| 18 | GET | `/cobranzas/alertas` | Listar alertas activas |
| 19 | PATCH | `/cobranzas/alertas/{id}/leer` | Marcar alerta leída |
| 20 | POST | `/cobranzas/alertas/marcar-todas-leidas` | Marcar todas leídas |
| 21 | DELETE | `/cobranzas/alertas/{id}` | Descartar alerta |
| 22 | GET | `/cobranzas/estrategias` | Listar estrategias |
| 23 | POST | `/cobranzas/estrategias` | Crear estrategia |
| 24 | PUT | `/cobranzas/estrategias/{id}` | Actualizar estrategia |
| 25 | DELETE | `/cobranzas/estrategias/{id}` | Eliminar estrategia |
| 26 | GET | `/cobranzas/comprobantes` | Listar comprobantes |
| 27 | GET | `/cobranzas/comprobantes/{id}` | Detalle de comprobante |
| 28 | POST | `/cobranzas/comprobantes/generar` | Generar comprobante manual |
| 29 | GET | `/cobranzas/comprobantes/{id}/pdf` | Descargar PDF |
| 30 | POST | `/cobranzas/comprobantes/{id}/anular` | Anular comprobante |
