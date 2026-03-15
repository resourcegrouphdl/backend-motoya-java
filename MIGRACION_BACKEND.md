# Módulo Migración Asistida — Guía de Implementación Backend

> **Base URL:** `{API_BASE}/api/migracion`
> **Autenticación:** Bearer Token (Firebase JWT) en header `Authorization`
> **Content-Type:** `application/json`
> **Rol requerido:** `COBRANZAS_SUPERVISOR` o `ADMIN`

---

## Contexto del Módulo

### Problema a resolver

Los clientes existentes están registrados en **Google Calendar** con el siguiente esquema:
- Un evento por cuota del cliente
- Título del evento: `"01. Juan Perez S/. 320"` → número de cuota + nombre + monto
- Color del evento: verde = cuota pagada, otro color = cuota pendiente
- Fecha del evento = fecha de vencimiento de esa cuota

Los datos faltantes (DNI, teléfono, modelo de moto, ID de contrato) están en registros físicos.

### Objetivo

Importar automáticamente los datos de Calendar, completar los datos faltantes mediante un formulario asistido, y migrar cada cliente al sistema de cobranzas definitivo.

### Flujo

```
Google Calendar API
        │
        ▼
POST /migracion/calendar/importar
  → Parsea eventos por cliente
  → Guarda documentos en colección `migracion_staging` (estado: INCOMPLETO)
        │
        ▼
GET /migracion/staging
  → El agente ve la lista de clientes en staging
  → Filtra los INCOMPLETO y abre el formulario de completado
        │
        ▼
PUT /migracion/staging/{id}
  → Guarda los datos faltantes (DNI, teléfono, moto, contratoId)
  → El registro pasa a estado COMPLETO
        │
        ▼
GET /migracion/staging/{id}/preview-cronograma
  → El agente revisa el cronograma generado antes de migrar
        │
        ▼
POST /migracion/staging/{id}/ejecutar
  → Crea el CasoActivo + cronograma + MovimientoDeuda en el sistema real
  → El registro pasa a estado MIGRADO
```

---

## Índice de Endpoints

| # | Método | Ruta | Descripción |
|---|--------|------|-------------|
| 1 | POST | `/migracion/calendar/importar` | Importar clientes desde Google Calendar |
| 2 | GET | `/migracion/staging` | Listar todos los registros en staging |
| 3 | PUT | `/migracion/staging/{id}` | Completar datos faltantes de un registro |
| 4 | DELETE | `/migracion/staging/{id}` | Eliminar un registro de staging |
| 5 | GET | `/migracion/staging/{id}/preview-cronograma` | Preview del cronograma antes de migrar |
| 6 | POST | `/migracion/staging/{id}/ejecutar` | Migrar un registro al sistema real |
| 7 | POST | `/migracion/staging/ejecutar-lote` | Migrar todos los registros en estado COMPLETO |

---

## 1. Importar desde Google Calendar

Conecta con la Google Calendar API usando las credenciales del sistema, obtiene todos los eventos del calendario de cobranzas, los agrupa por cliente y crea documentos en la colección `migracion_staging` de Firestore.

```
POST /api/migracion/calendar/importar
```

**Body:** vacío `{}`

**Lógica de parsing del backend:**

### Agrupación de eventos por cliente

Los eventos se agrupan por el nombre del cliente extraído del título. El título sigue el patrón:

```
"01. Juan Perez S/. 320"
 ├─ grupo 1: "01"       → número de cuota
 ├─ grupo 2: "Juan Perez" → nombre del cliente
 └─ grupo 3: "320"       → monto de la cuota
```

**Regex sugerido:**
```java
Pattern.compile("^(\\d+)\\.\\s+(.+?)\\s+S/\\.?\\s+([\\d,\\.]+)", Pattern.CASE_INSENSITIVE)
```

### Determinación del estado de pago

Usar el `colorId` del evento de Google Calendar para determinar si la cuota está pagada:

| colorId | Color | Significado |
|---------|-------|-------------|
| `2`     | Sage (verde) | Cuota **pagada** |
| `10`    | Basil (verde oscuro) | Cuota **pagada** |
| Cualquier otro | — | Cuota **pendiente** |

> **Nota:** Confirmar con el equipo qué colorId usan actualmente para "pagado". Puede configurarse como parámetro en el sistema.

### Anti-duplicados

Si ya existe un documento en `migracion_staging` con el mismo `clienteNombreCalendar` y `fechaInicio`, **no** crear uno nuevo. En su lugar, devolver cuántos fueron ignorados por duplicados.

**Response `200 OK`:**
```json
{
  "status":             "OK",
  "clientesDetectados": 15,
  "registrosCreados":   12,
  "duplicadosIgnorados": 3,
  "message":            "12 clientes importados desde Google Calendar. 3 ya existían en staging."
}
```

**Response `400 Bad Request`** (si el calendario no está configurado):
```json
{
  "status":  "ERROR",
  "message": "No se encontró la configuración del Google Calendar. Verificar credenciales en el sistema."
}
```

### Configuración necesaria en el backend

El backend necesita:
1. `GOOGLE_CALENDAR_ID`: ID del calendario de cobranzas (formato: `xxx@group.calendar.google.com`)
2. `GOOGLE_SERVICE_ACCOUNT_JSON`: credenciales de la service account con acceso de lectura al calendario
3. `MIGRACION_COLOR_PAGADO`: colorId que indica cuota pagada (default: `"2"`)

---

## 2. Listar Registros de Staging

```
GET /api/migracion/staging
```

**Query params (todos opcionales):**
| Param | Tipo | Descripción |
|-------|------|-------------|
| `estado` | `INCOMPLETO\|COMPLETO\|MIGRADO\|ERROR` | Filtrar por estado |

**Response `200 OK`:**
```json
[
  {
    "id":                    "STG-001",
    "estado":                "INCOMPLETO",
    "completitud":           50,

    "clienteNombreCalendar": "Juan Perez",
    "totalCuotas":           20,
    "montoCuota":            320.00,
    "capitalInferido":       6400.00,
    "fechaInicio":           "2025-01-15",
    "cuotasPagadas":         [1, 2, 3, 4, 5],
    "cronogramaCalendar": [
      { "cuota": 1,  "fechaVencimiento": "2025-01-15", "pagada": true,  "tituloOriginal": "01. Juan Perez S/. 320" },
      { "cuota": 2,  "fechaVencimiento": "2025-02-15", "pagada": true,  "tituloOriginal": "02. Juan Perez S/. 320" },
      { "cuota": 6,  "fechaVencimiento": "2025-06-15", "pagada": false, "tituloOriginal": "06. Juan Perez S/. 320" }
    ],

    "contratoId":      null,
    "clienteNombre":   null,
    "clienteDni":      null,
    "telefono":        null,
    "moto":            null,

    "contratoIdCreado": null,
    "errorDetalle":     null,
    "migradoEn":        null,
    "creadoEn":         "2026-03-15T10:00:00"
  },
  {
    "id":                    "STG-002",
    "estado":                "COMPLETO",
    "completitud":           100,
    "clienteNombreCalendar": "Maria Lopez",
    "totalCuotas":           24,
    "montoCuota":            300.00,
    "capitalInferido":       7200.00,
    "fechaInicio":           "2025-03-01",
    "cuotasPagadas":         [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
    "cronogramaCalendar":    [ /* ... 24 items ... */ ],
    "contratoId":      "CTR-002",
    "clienteNombre":   "María López Quispe",
    "clienteDni":      "87654321",
    "telefono":        "+51912345678",
    "moto":            "Honda Wave 110",
    "contratoIdCreado": null,
    "errorDetalle":    null,
    "migradoEn":       null,
    "creadoEn":        "2026-03-15T10:05:00"
  }
]
```

### Cálculo de `completitud`

```
completitud = (camposCompletos / totalCampos) * 100

Campos requeridos (5): contratoId, clienteNombre, clienteDni, telefono, moto
Si los 5 están presentes → completitud = 100 → estado = COMPLETO
Si alguno falta → completitud = (camposPresentes / 5) * 100 → estado = INCOMPLETO
```

---

## 3. Completar Datos Faltantes

```
PUT /api/migracion/staging/{id}
```

**Request body:**
```json
{
  "contratoId":    "CTR-001",
  "clienteNombre": "Juan Pérez Quispe",
  "clienteDni":    "12345678",
  "telefono":      "+51987654321",
  "moto":          "Bajaj Pulsar 150"
}
```

**Validaciones:**
- `contratoId`: no vacío, no debe existir ya en la colección `cobranzas_casos` de Firestore (validar unicidad)
- `clienteDni`: exactamente 8 dígitos numéricos
- `telefono`: formato `+51XXXXXXXXX` (9 dígitos después del código de país)
- `clienteNombre`: mínimo 3 caracteres
- `moto`: mínimo 3 caracteres

**Response `200 OK`:** Devuelve el registro staging completo actualizado con `estado: "COMPLETO"` y `completitud: 100`.

**Response `409 Conflict`** (si `contratoId` ya existe):
```json
{
  "status":  "ERROR",
  "message": "El contratoId CTR-001 ya existe en el sistema de cobranzas."
}
```

---

## 4. Eliminar Registro de Staging

```
DELETE /api/migracion/staging/{id}
```

Solo se puede eliminar documentos en estado `INCOMPLETO`, `COMPLETO` o `ERROR`. Los documentos `MIGRADO` no pueden eliminarse (son histórico de auditoría).

**Response `200 OK`:**
```json
{ "status": "OK", "message": "Registro eliminado del staging." }
```

**Response `409 Conflict`:**
```json
{ "status": "ERROR", "message": "No se puede eliminar un registro ya migrado." }
```

---

## 5. Preview del Cronograma

Genera el cronograma que se crearía en el sistema real, **sin persistir nada**. Permite al agente verificar que los datos son correctos antes de ejecutar la migración.

```
GET /api/migracion/staging/{id}/preview-cronograma
```

**Lógica de generación del cronograma:**

1. Tomar las `totalCuotas` y `montoCuota` del staging.
2. Para cada cuota del `cronogramaCalendar`:
   - Si `pagada = true` → `estado = PAGADA`
   - Si `pagada = false` y `fechaVencimiento < hoy` → `estado = VENCIDA`
   - Si `pagada = false` y `fechaVencimiento >= hoy` → `estado = VIGENTE`
3. Calcular `diasMoraEstimados` = días desde la primera cuota vencida no pagada hasta hoy.
4. Calcular `saldoEstimado` = `cuotasPendientes * montoCuota`.

**Response `200 OK`:**
```json
{
  "totalCuotas":       20,
  "montoCuota":        320.00,
  "capitalTotal":      6400.00,
  "cuotasPagadas":     5,
  "cuotasPendientes":  15,
  "diasMoraEstimados": 120,
  "saldoEstimado":     4800.00,
  "cronograma": [
    { "cuota": 1,  "fechaVencimiento": "2025-01-15", "estado": "PAGADA",  "monto": 320.00 },
    { "cuota": 2,  "fechaVencimiento": "2025-02-15", "estado": "PAGADA",  "monto": 320.00 },
    { "cuota": 5,  "fechaVencimiento": "2025-05-15", "estado": "PAGADA",  "monto": 320.00 },
    { "cuota": 6,  "fechaVencimiento": "2025-06-15", "estado": "VENCIDA", "monto": 320.00 },
    { "cuota": 15, "fechaVencimiento": "2026-03-15", "estado": "VENCIDA", "monto": 320.00 },
    { "cuota": 16, "fechaVencimiento": "2026-04-15", "estado": "VIGENTE", "monto": 320.00 },
    { "cuota": 20, "fechaVencimiento": "2026-08-15", "estado": "VIGENTE", "monto": 320.00 }
  ]
}
```

---

## 6. Ejecutar Migración (un registro)

Crea el caso en el sistema real de cobranzas a partir de los datos del staging. Es una operación **atómica**: si falla cualquier paso, no se persiste nada y el registro queda en estado `ERROR`.

```
POST /api/migracion/staging/{id}/ejecutar
```

**Body:** vacío `{}`

**Secuencia de operaciones que ejecuta el backend:**

```
1. Leer documento de migracion_staging/{id} — validar que exista y estado = COMPLETO
2. Verificar que cobranzas_casos/{contratoId} NO exista en Firestore
3. Construir Firestore Batch Write con todas las escrituras:
   a. SET cobranzas_casos/{contratoId}           ← documento del CasoActivo
   b. SET cobranzas_cronograma/{contratoId}/
          cuotas/{1..N}                          ← un documento por cuota
   c. ADD cobranzas_movimientos/{autoId}          ← MovimientoDeuda SALDO_INICIAL
   d. ADD cobranzas_eventos/{autoId}              ← EventoCobranza ESTADO_CAMBIADO
      payload: { estadoAnterior: null, estadoNuevo: "ACTIVO",
                 motivo: "Migrado desde Google Calendar" }
4. Ejecutar batch.commit() — Firestore garantiza atomicidad
5. UPDATE migracion_staging/{id}:
      estado → "MIGRADO"
      contratoIdCreado → contratoId
      migradoEn → Timestamp.now()
```

> Si el `batch.commit()` falla, ningún documento se persiste. El backend actualiza `migracion_staging/{id}` con `estado = "ERROR"` y `errorDetalle` para que el agente corrija y reintente.

**Response `200 OK`:**
```json
{
  "status":     "OK",
  "contratoId": "CTR-001",
  "message":    "Caso CTR-001 creado exitosamente. 15 cuotas pendientes. Saldo: S/ 4800.00"
}
```

**Response `200 OK` con error:**
```json
{
  "status":       "ERROR",
  "contratoId":   "CTR-001",
  "message":      "Error al crear el caso.",
  "errorDetalle": "El contratoId CTR-001 ya existe en el sistema de cobranzas."
}
```

> Si el batch falla, el backend actualiza `migracion_staging/{id}` con `estado = "ERROR"` y `errorDetalle`, para que el agente pueda corregir y reintentar.

### Datos que se crean en el sistema real

**`CasoActivo` creado:**
```json
{
  "contratoId":    "CTR-001",
  "cliente":       "Juan Pérez Quispe",
  "diasMora":      120,
  "deuda":         4800.00,
  "ultimaAccion":  "Migrado desde Google Calendar",
  "proximaAccion": "Contactar cliente — mora de 120 días",
  "prioridad":     "ALTA",
  "estado":        "INTERVENCION_REQUERIDA",
  "telefono":      "+51987654321"
}
```

**`MovimientoDeuda` inicial:**
```json
{
  "tipo":           "SALDO_INICIAL",
  "monto":          6400.00,
  "saldoAnterior":  0.00,
  "saldoNuevo":     6400.00,
  "descripcion":    "Saldo inicial al migrar desde Google Calendar",
  "autorizadoPor":  "SISTEMA"
}
```

---

## 7. Ejecutar Migración por Lote

Migra todos los registros en estado `COMPLETO` en una sola operación. Cada registro se procesa de forma independiente (el fallo de uno no detiene los demás).

```
POST /api/migracion/staging/ejecutar-lote
```

**Body (opcional):** si se envía vacío, procesa todos los COMPLETO. Si se envían IDs, solo esos:
```json
{
  "ids": ["STG-001", "STG-002", "STG-004"]
}
```

**Response `200 OK`:**
```json
{
  "migrados": 10,
  "errores":  2,
  "detalle": [
    { "id": "STG-001", "status": "OK",    "contratoId": "CTR-001" },
    { "id": "STG-003", "status": "ERROR", "contratoId": "CTR-003", "error": "Ya existe en el sistema." }
  ]
}
```

---

## Estructura Firestore — Colección de Staging

### Colección: `migracion_staging`

Cada documento representa un cliente detectado desde Calendar.
El **ID del documento** es generado automáticamente por Firestore (o puede usarse un slug del nombre del cliente para facilitar anti-duplicados).

```
migracion_staging/          ← colección
  {docId}/                  ← documento por cliente
    estado: string          // "INCOMPLETO" | "COMPLETO" | "MIGRADO" | "ERROR"
    completitud: number     // 0–100

    // Extraído de Calendar (auto-poblado)
    clienteNombreCalendar: string
    totalCuotas: number
    montoCuota: number
    capitalInferido: number
    fechaInicio: string            // "YYYY-MM-DD"
    cuotasPagadas: number[]        // [1, 2, 3, 4, 5]
    cronogramaCalendar: array      // ver sub-estructura abajo

    // Completado manualmente (null hasta que el agente lo llena)
    contratoId: string | null
    clienteNombre: string | null
    clienteDni: string | null
    telefono: string | null
    moto: string | null

    // Resultado de la migración
    contratoIdCreado: string | null
    errorDetalle: string | null
    migradoEn: Timestamp | null
    creadoEn: Timestamp
    actualizadoEn: Timestamp
```

### Sub-estructura de `cronogramaCalendar` (array de maps)

```
cronogramaCalendar: [
  {
    cuota: 1,
    fechaVencimiento: "2025-01-15",
    pagada: true,
    tituloOriginal: "01. Juan Perez S/. 320"
  },
  {
    cuota: 2,
    fechaVencimiento: "2025-02-15",
    pagada: false,
    tituloOriginal: "02. Juan Perez S/. 320"
  }
  // ... un elemento por cuota
]
```

### Consultas Firestore necesarias

```java
// Listar todos
firestore.collection("migracion_staging").get()

// Filtrar por estado
firestore.collection("migracion_staging")
  .whereEqualTo("estado", "INCOMPLETO")
  .get()

// Anti-duplicados al importar (buscar por nombre + fechaInicio)
firestore.collection("migracion_staging")
  .whereEqualTo("clienteNombreCalendar", nombreParsed)
  .whereEqualTo("fechaInicio", fechaInicio)
  .get()
```

### Colecciones del sistema real que se crean al migrar

Al ejecutar la migración, el backend escribe en estas colecciones usando un **Firestore Batch Write** para garantizar atomicidad:

```
cobranzas_casos/{contratoId}          ← documento del caso activo
cobranzas_cronograma/{contratoId}/cuotas/{n}  ← una subcolección por cuota
cobranzas_movimientos/{autoId}        ← MovimientoDeuda tipo SALDO_INICIAL
cobranzas_eventos/{autoId}            ← EventoCobranza tipo ESTADO_CAMBIADO
```

> **Nota sobre atomicidad en Firestore:** Firestore Batch Write soporta hasta 500 operaciones por lote. Para contratos con más de ~490 cuotas (improbable en este contexto), dividir en múltiples batches secuenciales.

---

## Esquemas de Entidades

### `MigracionStagingRecord`
```typescript
{
  id:                    string           // STG-001
  estado:                'INCOMPLETO' | 'COMPLETO' | 'MIGRADO' | 'ERROR'
  completitud:           number           // 0–100

  // Auto — Google Calendar
  clienteNombreCalendar: string
  totalCuotas:           number
  montoCuota:            number
  capitalInferido:       number
  fechaInicio:           string           // YYYY-MM-DD
  cuotasPagadas:         number[]         // [1,2,3,4,5]
  cronogramaCalendar:    CuotaCalendar[]

  // Manual — a completar
  contratoId:            string | null
  clienteNombre:         string | null
  clienteDni:            string | null
  telefono:              string | null
  moto:                  string | null

  // Resultado
  contratoIdCreado:      string | null
  errorDetalle:          string | null
  migradoEn:             string | null    // ISO timestamp
  creadoEn:              string           // ISO timestamp
}
```

### `CuotaCalendar`
```typescript
{
  cuota:            number   // número de cuota (1, 2, 3...)
  fechaVencimiento: string   // YYYY-MM-DD (fecha del evento en Calendar)
  pagada:           boolean  // true si colorId indica pagado
  tituloOriginal:   string   // texto raw del evento para auditoría
}
```

### `CompletarStagingDto` (request PUT)
```typescript
{
  contratoId:    string   // requerido, único
  clienteNombre: string   // requerido, mínimo 3 chars
  clienteDni:    string   // requerido, 8 dígitos
  telefono:      string   // requerido, formato +51XXXXXXXXX
  moto:          string   // requerido, mínimo 3 chars
}
```

### `ImportarCalendarResponse`
```typescript
{
  status:              'OK' | 'ERROR'
  clientesDetectados:  number
  registrosCreados:    number
  duplicadosIgnorados: number
  message:             string
}
```

### `EjecutarMigracionResponse`
```typescript
{
  status:        'OK' | 'ERROR'
  contratoId:    string
  message:       string
  errorDetalle?: string
}
```

---

## Reglas de Negocio

1. **Unicidad de `contratoId`**: antes de ejecutar la migración, verificar que el documento `cobranzas_casos/{contratoId}` no exista en Firestore. Si existe, marcar el staging como `ERROR`.

2. **Solo migrar COMPLETO**: el endpoint `/ejecutar` devuelve `400` si el registro está en estado `INCOMPLETO`, `MIGRADO` o `ERROR` sin corrección previa.

3. **Anti-duplicados en importación**: si se llama a `/importar` dos veces, los clientes ya existentes en staging no se duplican (verificar por `clienteNombreCalendar` + `fechaInicio`).

4. **Prioridad calculada al migrar**: `diasMoraEstimados >= 30` → `ALTA`. `15–29` → `MEDIA`. `1–14` → `BAJA`. `0` → sin mora.

5. **Cuotas pagadas**: al crear el cronograma, marcar las cuotas de `cuotasPagadas[]` como `PAGADA` y registrar un `MovimientoDeuda` de tipo `PAGO_CUOTA` por cada una (con `autorizadoPor = "MIGRACION_CALENDAR"`).

6. **El módulo es idempotente**: se puede importar N veces desde Calendar sin duplicar datos. Los registros ya en staging se actualizan solo si el agente lo decide manualmente (re-importación no sobreescribe staging existente).

7. **Flag de activación**: el módulo puede desactivarse desde el frontend cambiando `environment.features.migracionAsistida = false`. El backend puede implementar un endpoint adicional `GET /api/migracion/estado` que devuelva si la migración está completa (`migrados / total = 100%`) para mostrar un indicador en el panel de administración.

---

## Configuración de Google Calendar API

El backend necesita una **Service Account** de Google Cloud con acceso al calendario:

```
1. En Google Cloud Console → IAM → Service Accounts → Crear
2. Descargar el JSON de credenciales
3. En Google Calendar → Configuración del calendario → Compartir → Agregar service account email con permiso "Ver todos los detalles de los eventos"
4. Guardar en variables de entorno del Cloud Run:
   - GOOGLE_CALENDAR_ID=xxx@group.calendar.google.com
   - GOOGLE_CREDENTIALS_JSON={"type":"service_account",...}
   - MIGRACION_COLOR_PAGADO_IDS=2,10  (colorIds que indican cuota pagada)
```

**Dependencias Maven (Spring Boot):**
```xml
<!-- Google Calendar API -->
<dependency>
  <groupId>com.google.apis</groupId>
  <artifactId>google-api-services-calendar</artifactId>
  <version>v3-rev20231001-2.0.0</version>
</dependency>

<!-- Firebase Admin SDK (Firestore) -->
<dependency>
  <groupId>com.google.firebase</groupId>
  <artifactId>firebase-admin</artifactId>
  <version>9.2.0</version>
</dependency>
```

### Inicialización del Firebase Admin SDK en Spring Boot

```java
@Configuration
public class FirebaseConfig {

    @Bean
    public Firestore firestore() throws IOException {
        // En Cloud Run, usar Application Default Credentials (ADC) automáticamente
        // No hace falta archivo de credenciales si el service account del Cloud Run
        // tiene el rol "Cloud Datastore User" en el proyecto Firebase.
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setProjectId("motoya-form")   // ← projectId del proyecto Firebase
            .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
        return FirestoreClient.getFirestore();
    }
}
```

### Ejemplo de escritura atómica con Batch Write

```java
@Service
public class MigracionEjecutorService {

    @Autowired private Firestore firestore;

    public void ejecutar(MigracionStagingRecord staging) throws Exception {
        WriteBatch batch = firestore.batch();

        // 1. CasoActivo
        DocumentReference casoRef = firestore
            .collection("cobranzas_casos")
            .document(staging.getContratoId());
        batch.set(casoRef, buildCasoActivo(staging));

        // 2. Cuotas del cronograma
        for (CuotaCalendar cuota : staging.getCronogramaCalendar()) {
            DocumentReference cuotaRef = firestore
                .collection("cobranzas_cronograma")
                .document(staging.getContratoId())
                .collection("cuotas")
                .document(String.valueOf(cuota.getCuota()));
            batch.set(cuotaRef, buildCuota(cuota, staging.getMontoCuota()));
        }

        // 3. MovimientoDeuda inicial
        DocumentReference movRef = firestore
            .collection("cobranzas_movimientos")
            .document();
        batch.set(movRef, buildMovimientoInicial(staging));

        // 4. EventoCobranza
        DocumentReference eventoRef = firestore
            .collection("cobranzas_eventos")
            .document();
        batch.set(eventoRef, buildEventoMigracion(staging));

        // Commit atómico — si falla, nada se escribe
        batch.commit().get();

        // 5. Actualizar staging como MIGRADO
        firestore.collection("migracion_staging")
            .document(staging.getId())
            .update(Map.of(
                "estado", "MIGRADO",
                "contratoIdCreado", staging.getContratoId(),
                "migradoEn", Timestamp.now()
            ));
    }
}
```
