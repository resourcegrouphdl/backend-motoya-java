# MODELO DE BASE DE DATOS - SISTEMA DE EVALUACION DE CREDITOS

## Arquitectura: Event Sourcing + CQRS + PostgreSQL

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ARQUITECTURA DE DATOS                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐    │
│  │   EVENT STORE    │     │  STATE TABLES    │     │ MATERIALIZED     │    │
│  │   (Inmutable)    │────►│  (Current State) │────►│    VIEWS         │    │
│  │                  │     │                  │     │  (Fast Reads)    │    │
│  │ evaluacion_evento│     │ evaluacion       │     │ evaluacion_lista │    │
│  │                  │     │ persona          │     │ dashboard_stats  │    │
│  │                  │     │ documento        │     │                  │    │
│  │                  │     │ referencia       │     │                  │    │
│  │                  │     │ financiamiento   │     │                  │    │
│  └──────────────────┘     └──────────────────┘     └──────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. TABLAS PRINCIPALES (State Tables)

### 1.1 evaluacion (Aggregate Root)

```sql
CREATE TABLE evaluacion (
    -- Identificadores
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_evaluacion       VARCHAR(20) NOT NULL UNIQUE,
    codigo_solicitud        VARCHAR(20) NOT NULL,
    solicitud_firebase_id   VARCHAR(100),

    -- Estado y Progreso
    estado                  VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE',
    etapa                   VARCHAR(50) NOT NULL DEFAULT 'RECEPCION',
    prioridad               VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    progreso_porcentaje     INTEGER DEFAULT 0 CHECK (progreso_porcentaje BETWEEN 0 AND 100),

    -- Relaciones a personas
    titular_id              UUID NOT NULL REFERENCES persona(id),
    fiador_id               UUID REFERENCES persona(id),

    -- Relación a vehículo
    vehiculo_id             UUID NOT NULL REFERENCES vehiculo(id),

    -- Relación a financiamiento
    financiamiento_id       UUID NOT NULL REFERENCES financiamiento(id),

    -- Asignación
    asignado_a              VARCHAR(100),
    nombre_evaluador        VARCHAR(200),

    -- Tienda y Vendedor
    tienda_id               VARCHAR(100) NOT NULL,
    tienda_nombre           VARCHAR(200),
    tienda_codigo           VARCHAR(20),
    vendedor_id             VARCHAR(100) NOT NULL,
    vendedor_nombre         VARCHAR(200),
    vendedor_telefono       VARCHAR(20),
    vendedor_email          VARCHAR(100),

    -- Scores
    score_documental        DECIMAL(5,2),
    score_referencias       DECIMAL(5,2),
    score_crediticio        DECIMAL(5,2),
    score_ingresos          DECIMAL(5,2),
    score_entrevista_titular DECIMAL(5,2),
    score_entrevista_fiador DECIMAL(5,2),
    score_final             DECIMAL(5,2),

    -- Decision Final
    decision                VARCHAR(30),
    decision_motivo         TEXT,
    decision_condiciones    JSONB,
    decision_fecha          TIMESTAMP WITH TIME ZONE,
    decision_por            VARCHAR(100),
    decision_nombre         VARCHAR(200),

    -- Montos aprobados
    inicial_aprobada        DECIMAL(12,2),
    monto_financiar_aprobado DECIMAL(12,2),
    cuota_aprobada          DECIMAL(12,2),

    -- Control de versiones (Optimistic Locking)
    version                 INTEGER NOT NULL DEFAULT 1,

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    creado_por              VARCHAR(100),
    actualizado_por         VARCHAR(100),

    -- Constraints
    CONSTRAINT chk_estado CHECK (estado IN (
        'PENDIENTE', 'EN_REVISION_INICIAL', 'VERIFICANDO_DOCUMENTOS',
        'DOCUMENTOS_OBSERVADOS', 'VERIFICANDO_REFERENCIAS', 'CONSULTANDO_CENTRALES',
        'EVALUANDO_INGRESOS', 'ENTREVISTA_PROGRAMADA', 'ENTREVISTA_COMPLETADA',
        'EN_REVISION_FINAL', 'APROBADO', 'APROBADO_CONDICIONAL', 'RECHAZADO',
        'OBSERVADO', 'EN_ESPERA', 'CANCELADO', 'PAUSADO'
    )),
    CONSTRAINT chk_etapa CHECK (etapa IN (
        'RECEPCION', 'REVISION_DOCUMENTAL', 'VERIFICACION_REFERENCIAS',
        'CONSULTA_CENTRALES', 'EVALUACION_INGRESOS', 'ENTREVISTA', 'DECISION_FINAL'
    )),
    CONSTRAINT chk_prioridad CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA', 'NORMAL')),
    CONSTRAINT chk_decision CHECK (decision IS NULL OR decision IN (
        'APROBADO', 'APROBADO_CONDICIONAL', 'RECHAZADO', 'REQUIERE_COMITE'
    ))
);

-- Índices para búsquedas frecuentes
CREATE INDEX idx_evaluacion_estado ON evaluacion(estado);
CREATE INDEX idx_evaluacion_etapa ON evaluacion(etapa);
CREATE INDEX idx_evaluacion_prioridad ON evaluacion(prioridad);
CREATE INDEX idx_evaluacion_asignado ON evaluacion(asignado_a);
CREATE INDEX idx_evaluacion_tienda ON evaluacion(tienda_id);
CREATE INDEX idx_evaluacion_vendedor ON evaluacion(vendedor_id);
CREATE INDEX idx_evaluacion_creado ON evaluacion(creado_en DESC);
CREATE INDEX idx_evaluacion_numero ON evaluacion(numero_evaluacion);
CREATE INDEX idx_evaluacion_codigo_solicitud ON evaluacion(codigo_solicitud);

-- Índice compuesto para filtros comunes
CREATE INDEX idx_evaluacion_estado_prioridad ON evaluacion(estado, prioridad, creado_en DESC);
```

---

### 1.2 persona (Titular y Fiador)

```sql
CREATE TABLE persona (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo                    VARCHAR(20) NOT NULL, -- 'TITULAR', 'FIADOR'

    -- Datos personales
    nombres                 VARCHAR(100) NOT NULL,
    apellido_paterno        VARCHAR(100) NOT NULL,
    apellido_materno        VARCHAR(100),
    nombre_completo         VARCHAR(300) GENERATED ALWAYS AS (
        TRIM(nombres || ' ' || apellido_paterno || ' ' || COALESCE(apellido_materno, ''))
    ) STORED,

    -- Documento de identidad
    tipo_documento          VARCHAR(30) NOT NULL DEFAULT 'DNI',
    numero_documento        VARCHAR(20) NOT NULL,
    nacionalidad            VARCHAR(50) DEFAULT 'peruana',

    -- Datos demográficos
    sexo                    VARCHAR(20),
    fecha_nacimiento        DATE,
    edad                    INTEGER,
    estado_civil            VARCHAR(30),
    cargas_familiares       INTEGER DEFAULT 0,

    -- Contacto
    email                   VARCHAR(150),
    telefono1               VARCHAR(20),
    telefono2               VARCHAR(20),

    -- Domicilio
    departamento            VARCHAR(100),
    provincia               VARCHAR(100),
    distrito                VARCHAR(100),
    direccion               VARCHAR(300),
    direccion_completa      VARCHAR(500),
    tipo_vivienda           VARCHAR(30),
    antiguedad_domiciliaria VARCHAR(50),
    referencia_ubicacion    TEXT,
    ubicacion_gps_lat       DECIMAL(10,8),
    ubicacion_gps_lng       DECIMAL(11,8),

    -- Datos laborales
    ocupacion               VARCHAR(100),
    tipo_trabajo            VARCHAR(30),
    nombre_empresa          VARCHAR(200),
    direccion_trabajo       VARCHAR(300),
    ubicacion_trabajo_lat   DECIMAL(10,8),
    ubicacion_trabajo_lng   DECIMAL(11,8),
    antiguedad_trabajo      VARCHAR(50),
    ingreso_mensual         DECIMAL(12,2),
    rango_ingresos          VARCHAR(50),

    -- Licencia de conducir
    licencia_conducir       VARCHAR(20),
    numero_licencia         VARCHAR(30),
    vencimiento_licencia    DATE,
    licencia_vigente        BOOLEAN DEFAULT false,

    -- Archivos (URLs de Storage)
    archivos                JSONB DEFAULT '{}',

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tipo_persona CHECK (tipo IN ('TITULAR', 'FIADOR')),
    CONSTRAINT chk_tipo_documento CHECK (tipo_documento IN ('DNI', 'PASAPORTE', 'CARNET_EXTRANJERIA')),
    CONSTRAINT chk_sexo CHECK (sexo IS NULL OR sexo IN ('MASCULINO', 'FEMENINO')),
    CONSTRAINT chk_estado_civil CHECK (estado_civil IS NULL OR estado_civil IN (
        'SOLTERO', 'CASADO', 'DIVORCIADO', 'VIUDO', 'CONVIVIENTE'
    )),
    CONSTRAINT chk_tipo_vivienda CHECK (tipo_vivienda IS NULL OR tipo_vivienda IN (
        'PROPIA', 'ALQUILADA', 'FAMILIAR', 'OTROS'
    )),
    CONSTRAINT chk_tipo_trabajo CHECK (tipo_trabajo IS NULL OR tipo_trabajo IN (
        'DEPENDIENTE', 'INDEPENDIENTE', 'JUBILADO', 'DESEMPLEADO'
    ))
);

-- Índices
CREATE INDEX idx_persona_tipo ON persona(tipo);
CREATE INDEX idx_persona_documento ON persona(numero_documento);
CREATE INDEX idx_persona_nombre ON persona(nombre_completo);
CREATE UNIQUE INDEX idx_persona_tipo_documento_unique ON persona(tipo_documento, numero_documento);
```

---

### 1.3 vehiculo

```sql
CREATE TABLE vehiculo (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Datos del vehículo
    marca                   VARCHAR(100) NOT NULL,
    modelo                  VARCHAR(100) NOT NULL,
    anio                    VARCHAR(4),
    color                   VARCHAR(50),
    cilindrada              INTEGER,
    descripcion_completa    VARCHAR(300),

    -- Precio
    precio_referencial      DECIMAL(12,2) NOT NULL,

    -- Metadata
    firebase_vehiculo_id    VARCHAR(100),

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehiculo_marca_modelo ON vehiculo(marca, modelo);
```

---

### 1.4 financiamiento

```sql
CREATE TABLE financiamiento (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id               UUID REFERENCES evaluacion(id),

    -- Costos base
    monto_vehiculo              DECIMAL(12,2) NOT NULL,
    soat_costos_notariales      DECIMAL(12,2),
    costo_total                 DECIMAL(12,2),

    -- Financiamiento original (lo que solicitó el cliente)
    inicial_original            DECIMAL(12,2) NOT NULL,
    monto_financiar_original    DECIMAL(12,2) NOT NULL,
    numero_cuotas_quincenales   INTEGER NOT NULL,
    monto_cuota_quincenal       DECIMAL(12,2) NOT NULL,

    -- Financiamiento ajustado (modificado por evaluador)
    inicial_ajustada            DECIMAL(12,2),
    monto_financiar_ajustado    DECIMAL(12,2),
    monto_cuota_ajustada        DECIMAL(12,2),

    -- Tasa de interés
    tasa_interes_mensual        DECIMAL(6,4),
    tasa_interes_anual          DECIMAL(6,4),

    -- Indicadores calculados
    porcentaje_inicial          DECIMAL(5,2),
    relacion_cuota_ingreso      DECIMAL(5,2),
    capacidad_pago              VARCHAR(20),

    -- Auditoría
    creado_en                   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_capacidad_pago CHECK (capacidad_pago IS NULL OR capacidad_pago IN (
        'ALTA', 'MEDIA', 'BAJA', 'INSUFICIENTE'
    ))
);

CREATE INDEX idx_financiamiento_evaluacion ON financiamiento(evaluacion_id);
```

---

### 1.5 documento

```sql
CREATE TABLE documento (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id           UUID NOT NULL REFERENCES evaluacion(id),

    -- Tipo y persona
    tipo                    VARCHAR(50) NOT NULL,
    tipo_persona            VARCHAR(20) NOT NULL,
    nombre                  VARCHAR(200),

    -- Estado de validación
    estado                  VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    validado                BOOLEAN DEFAULT false,

    -- URL del archivo
    url                     TEXT,

    -- Validación
    validado_por            VARCHAR(100),
    fecha_validacion        TIMESTAMP WITH TIME ZONE,
    observaciones           TEXT,

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tipo_documento CHECK (tipo IN (
        'DNI_FRENTE', 'DNI_REVERSO', 'PASAPORTE', 'CARNET_EXTRANJERIA',
        'LICENCIA_CONDUCIR', 'COMPROBANTE_DOMICILIO', 'RECIBO_SERVICIO',
        'COMPROBANTE_INGRESOS', 'BOLETA_PAGO', 'FACHADA_DOMICILIO', 'OTROS'
    )),
    CONSTRAINT chk_tipo_persona_doc CHECK (tipo_persona IN ('TITULAR', 'FIADOR')),
    CONSTRAINT chk_estado_doc CHECK (estado IN ('PENDIENTE', 'VALIDADO', 'RECHAZADO', 'OBSERVADO'))
);

CREATE INDEX idx_documento_evaluacion ON documento(evaluacion_id);
CREATE INDEX idx_documento_tipo ON documento(tipo, tipo_persona);
CREATE INDEX idx_documento_estado ON documento(estado);
```

---

### 1.6 referencia

```sql
CREATE TABLE referencia (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id           UUID NOT NULL REFERENCES evaluacion(id),

    -- Número de orden
    numero                  INTEGER NOT NULL,

    -- Datos de la referencia
    nombre                  VARCHAR(100) NOT NULL,
    apellidos               VARCHAR(150),
    nombre_completo         VARCHAR(300),
    telefono                VARCHAR(20) NOT NULL,
    parentesco              VARCHAR(50),

    -- Verificación
    verificada              BOOLEAN DEFAULT false,
    resultado_verificacion  VARCHAR(30),
    fecha_verificacion      TIMESTAMP WITH TIME ZONE,
    verificado_por          VARCHAR(100),
    nombre_verificador      VARCHAR(200),
    observaciones           TEXT,

    -- Score
    score_verificacion      DECIMAL(5,2),

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_resultado_verificacion CHECK (resultado_verificacion IS NULL OR
        resultado_verificacion IN ('POSITIVA', 'NEGATIVA', 'NO_CONTESTA', 'TELEFONO_INVALIDO'))
);

CREATE INDEX idx_referencia_evaluacion ON referencia(evaluacion_id);
CREATE INDEX idx_referencia_verificada ON referencia(verificada);
CREATE UNIQUE INDEX idx_referencia_evaluacion_numero ON referencia(evaluacion_id, numero);
```

---

### 1.7 entrevista

```sql
CREATE TABLE entrevista (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id           UUID NOT NULL REFERENCES evaluacion(id),
    tipo_persona            VARCHAR(20) NOT NULL,

    -- Evaluación de actitud (1-5)
    actitud                 INTEGER CHECK (actitud BETWEEN 1 AND 5),
    disposicion             INTEGER CHECK (disposicion BETWEEN 1 AND 5),
    claridad                INTEGER CHECK (claridad BETWEEN 1 AND 5),

    -- Evaluación de estabilidad (1-5)
    estabilidad_laboral     INTEGER CHECK (estabilidad_laboral BETWEEN 1 AND 5),
    estabilidad_domiciliaria INTEGER CHECK (estabilidad_domiciliaria BETWEEN 1 AND 5),
    historico_crediticio    INTEGER CHECK (historico_crediticio BETWEEN 1 AND 5),

    -- Capacidad económica
    ingreso_verificable     BOOLEAN,
    comprobante_ingresos    BOOLEAN,
    gastos_mensuales        DECIMAL(12,2),
    capacidad_pago_calculada DECIMAL(12,2),

    -- Score y recomendación
    score_total             DECIMAL(5,2),
    recomendacion           VARCHAR(30),

    -- Observaciones
    observaciones           TEXT,

    -- Metadata de la entrevista
    fecha_entrevista        TIMESTAMP WITH TIME ZONE,
    duracion_minutos        INTEGER,
    realizado_por           VARCHAR(100),
    nombre_entrevistador    VARCHAR(200),

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tipo_persona_ent CHECK (tipo_persona IN ('TITULAR', 'FIADOR')),
    CONSTRAINT chk_recomendacion CHECK (recomendacion IS NULL OR recomendacion IN (
        'APROBAR', 'RECHAZAR', 'CONDICIONAL', 'FAVORABLE', 'DESFAVORABLE', 'NEUTRAL'
    ))
);

CREATE INDEX idx_entrevista_evaluacion ON entrevista(evaluacion_id);
CREATE UNIQUE INDEX idx_entrevista_evaluacion_persona ON entrevista(evaluacion_id, tipo_persona);
```

---

### 1.8 alerta

```sql
CREATE TABLE alerta (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id           UUID NOT NULL REFERENCES evaluacion(id),

    -- Tipo y severidad
    tipo                    VARCHAR(50) NOT NULL,
    severidad               VARCHAR(20) NOT NULL DEFAULT 'MEDIA',

    -- Contenido
    mensaje                 VARCHAR(500) NOT NULL,
    descripcion             TEXT,

    -- Estado
    resuelta                BOOLEAN DEFAULT false,

    -- Resolución
    fecha_resolucion        TIMESTAMP WITH TIME ZONE,
    resuelta_por            VARCHAR(100),
    resolucion_nota         TEXT,

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_severidad CHECK (severidad IN ('ALTA', 'MEDIA', 'BAJA', 'INFO', 'WARNING', 'ERROR'))
);

CREATE INDEX idx_alerta_evaluacion ON alerta(evaluacion_id);
CREATE INDEX idx_alerta_resuelta ON alerta(resuelta);
CREATE INDEX idx_alerta_severidad ON alerta(severidad);
```

---

### 1.9 etapa_evaluacion (Tracking de etapas)

```sql
CREATE TABLE etapa_evaluacion (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id           UUID NOT NULL REFERENCES evaluacion(id),

    -- Etapa
    numero                  INTEGER NOT NULL,
    nombre                  VARCHAR(100) NOT NULL,
    descripcion             TEXT,

    -- Estado
    estado                  VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',

    -- Tiempos
    fecha_inicio            TIMESTAMP WITH TIME ZONE,
    fecha_fin               TIMESTAMP WITH TIME ZONE,

    -- Quién completó
    completado_por          VARCHAR(100),
    nombre_completador      VARCHAR(200),
    observaciones           TEXT,

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_estado_etapa CHECK (estado IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA', 'OMITIDA'))
);

CREATE INDEX idx_etapa_evaluacion ON etapa_evaluacion(evaluacion_id);
CREATE UNIQUE INDEX idx_etapa_evaluacion_numero ON etapa_evaluacion(evaluacion_id, numero);
```

---

## 2. EVENT STORE (Inmutable)

### 2.1 evaluacion_evento

```sql
CREATE TABLE evaluacion_evento (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluacion_id           UUID NOT NULL,

    -- Tipo de evento
    tipo_evento             VARCHAR(50) NOT NULL,

    -- Payload del evento (JSONB para flexibilidad)
    payload                 JSONB NOT NULL DEFAULT '{}',

    -- Versión para ordenamiento y concurrencia
    version                 INTEGER NOT NULL,

    -- Usuario que generó el evento
    usuario_id              VARCHAR(100) NOT NULL,
    usuario_nombre          VARCHAR(200),

    -- Metadata
    ip_origen               VARCHAR(45),
    user_agent              TEXT,

    -- Timestamp inmutable
    timestamp               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Constraint para garantizar orden de eventos
    CONSTRAINT uk_evaluacion_version UNIQUE (evaluacion_id, version)
);

-- Índices para consultas de eventos
CREATE INDEX idx_evento_evaluacion ON evaluacion_evento(evaluacion_id);
CREATE INDEX idx_evento_tipo ON evaluacion_evento(tipo_evento);
CREATE INDEX idx_evento_timestamp ON evaluacion_evento(timestamp DESC);
CREATE INDEX idx_evento_usuario ON evaluacion_evento(usuario_id);

-- Índice para replay de eventos
CREATE INDEX idx_evento_evaluacion_version ON evaluacion_evento(evaluacion_id, version);

-- Índice GIN para búsquedas en payload
CREATE INDEX idx_evento_payload ON evaluacion_evento USING GIN (payload);

-- Comentario para documentar que esta tabla es APPEND-ONLY
COMMENT ON TABLE evaluacion_evento IS
'Event Store inmutable. Solo INSERT, nunca UPDATE ni DELETE.
Cada evento representa un cambio en el estado de la evaluación.';
```

---

## 3. VISTAS MATERIALIZADAS (Fast Reads)

### 3.1 evaluacion_lista_mv (Para tablas y listas)

```sql
CREATE MATERIALIZED VIEW evaluacion_lista_mv AS
SELECT
    e.id,
    e.numero_evaluacion,
    e.codigo_solicitud,
    e.estado,
    e.etapa,
    e.prioridad,
    e.progreso_porcentaje AS progreso,
    e.score_final,

    -- Datos del titular (desnormalizados)
    t.nombre_completo AS nombre_titular,
    t.numero_documento AS documento_titular,
    t.telefono1 AS telefono_titular,

    -- Vehículo
    v.descripcion_completa AS vehiculo_descripcion,
    v.precio_referencial AS monto_vehiculo,

    -- Financiamiento
    COALESCE(f.monto_financiar_ajustado, f.monto_financiar_original) AS monto_financiar,

    -- Asignación
    e.asignado_a,
    e.nombre_evaluador,

    -- Tienda
    e.tienda_id,
    e.tienda_nombre,

    -- Indicadores
    (SELECT COUNT(*) FROM alerta a WHERE a.evaluacion_id = e.id AND NOT a.resuelta) AS alertas_count,
    (e.fiador_id IS NOT NULL) AS tiene_fiador,

    -- Timestamps
    e.creado_en,
    e.actualizado_en

FROM evaluacion e
JOIN persona t ON e.titular_id = t.id
JOIN vehiculo v ON e.vehiculo_id = v.id
LEFT JOIN financiamiento f ON e.financiamiento_id = f.id;

-- Índices en la vista materializada
CREATE UNIQUE INDEX idx_mv_lista_id ON evaluacion_lista_mv(id);
CREATE INDEX idx_mv_lista_estado ON evaluacion_lista_mv(estado);
CREATE INDEX idx_mv_lista_prioridad ON evaluacion_lista_mv(prioridad);
CREATE INDEX idx_mv_lista_asignado ON evaluacion_lista_mv(asignado_a);
CREATE INDEX idx_mv_lista_tienda ON evaluacion_lista_mv(tienda_id);
CREATE INDEX idx_mv_lista_creado ON evaluacion_lista_mv(creado_en DESC);

-- Función para refrescar la vista
CREATE OR REPLACE FUNCTION refresh_evaluacion_lista()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY evaluacion_lista_mv;
END;
$$ LANGUAGE plpgsql;
```

### 3.2 dashboard_estadisticas_mv

```sql
CREATE MATERIALIZED VIEW dashboard_estadisticas_mv AS
SELECT
    -- Totales
    COUNT(*) AS total_evaluaciones,
    COUNT(*) FILTER (WHERE estado = 'PENDIENTE') AS pendientes,
    COUNT(*) FILTER (WHERE estado NOT IN ('APROBADO', 'APROBADO_CONDICIONAL', 'RECHAZADO', 'CANCELADO')) AS en_proceso,
    COUNT(*) FILTER (WHERE estado IN ('APROBADO', 'APROBADO_CONDICIONAL')) AS aprobadas,
    COUNT(*) FILTER (WHERE estado = 'RECHAZADO') AS rechazadas,
    COUNT(*) FILTER (WHERE estado = 'OBSERVADO') AS observadas,
    COUNT(*) FILTER (WHERE estado = 'CANCELADO') AS canceladas,

    -- Por prioridad
    COUNT(*) FILTER (WHERE prioridad = 'ALTA') AS prioridad_alta,
    COUNT(*) FILTER (WHERE prioridad = 'MEDIA') AS prioridad_media,
    COUNT(*) FILTER (WHERE prioridad = 'BAJA') AS prioridad_baja,
    COUNT(*) FILTER (WHERE prioridad = 'NORMAL') AS prioridad_normal,

    -- Sin asignar
    COUNT(*) FILTER (WHERE asignado_a IS NULL AND estado NOT IN ('APROBADO', 'RECHAZADO', 'CANCELADO')) AS pendientes_asignacion,

    -- Hoy
    COUNT(*) FILTER (WHERE DATE(creado_en) = CURRENT_DATE) AS evaluaciones_hoy,
    COUNT(*) FILTER (WHERE DATE(actualizado_en) = CURRENT_DATE AND estado IN ('APROBADO', 'APROBADO_CONDICIONAL')) AS aprobadas_hoy,
    COUNT(*) FILTER (WHERE DATE(actualizado_en) = CURRENT_DATE AND estado = 'RECHAZADO') AS rechazadas_hoy,

    -- Esta semana
    COUNT(*) FILTER (WHERE creado_en >= DATE_TRUNC('week', CURRENT_DATE)) AS evaluaciones_semana,

    -- Este mes
    COUNT(*) FILTER (WHERE creado_en >= DATE_TRUNC('month', CURRENT_DATE)) AS evaluaciones_mes,

    -- Promedios
    AVG(score_final) FILTER (WHERE score_final IS NOT NULL) AS promedio_score,
    AVG(progreso_porcentaje) AS promedio_progreso,

    -- Tasa de aprobación (últimos 30 días)
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE estado IN ('APROBADO', 'APROBADO_CONDICIONAL') AND actualizado_en >= CURRENT_DATE - INTERVAL '30 days') /
        NULLIF(COUNT(*) FILTER (WHERE estado IN ('APROBADO', 'APROBADO_CONDICIONAL', 'RECHAZADO') AND actualizado_en >= CURRENT_DATE - INTERVAL '30 days'), 0)
    , 2) AS tasa_aprobacion_30d,

    -- Timestamp de última actualización
    CURRENT_TIMESTAMP AS actualizado_en

FROM evaluacion;

-- Índice único para refresh concurrente
CREATE UNIQUE INDEX idx_mv_dashboard ON dashboard_estadisticas_mv(actualizado_en);
```

### 3.3 estadisticas_por_tienda_mv

```sql
CREATE MATERIALIZED VIEW estadisticas_por_tienda_mv AS
SELECT
    tienda_id,
    tienda_nombre,
    COUNT(*) AS total,
    COUNT(*) FILTER (WHERE estado IN ('APROBADO', 'APROBADO_CONDICIONAL')) AS aprobadas,
    COUNT(*) FILTER (WHERE estado = 'RECHAZADO') AS rechazadas,
    COUNT(*) FILTER (WHERE estado NOT IN ('APROBADO', 'APROBADO_CONDICIONAL', 'RECHAZADO', 'CANCELADO')) AS en_proceso,
    AVG(score_final) FILTER (WHERE score_final IS NOT NULL) AS promedio_score,
    SUM(CASE WHEN estado IN ('APROBADO', 'APROBADO_CONDICIONAL') THEN
        (SELECT COALESCE(monto_financiar_aprobado, monto_financiar_original) FROM financiamiento WHERE id = evaluacion.financiamiento_id)
    ELSE 0 END) AS monto_total_aprobado,
    CURRENT_TIMESTAMP AS actualizado_en
FROM evaluacion
GROUP BY tienda_id, tienda_nombre;

CREATE UNIQUE INDEX idx_mv_tienda ON estadisticas_por_tienda_mv(tienda_id);
```

---

## 4. TABLAS DE SOPORTE

### 4.1 usuario (Para tracking de usuarios del sistema)

```sql
CREATE TABLE usuario (
    id                      VARCHAR(100) PRIMARY KEY, -- UID de Firebase
    email                   VARCHAR(150) NOT NULL,
    nombre_completo         VARCHAR(200),
    primer_nombre           VARCHAR(100),
    apellidos               VARCHAR(100),
    tipo_usuario            VARCHAR(30) NOT NULL,
    activo                  BOOLEAN DEFAULT true,

    -- Tiendas asociadas
    tiendas                 JSONB DEFAULT '[]',

    -- Metadata
    primer_login            BOOLEAN DEFAULT true,
    ultimo_login            TIMESTAMP WITH TIME ZONE,

    -- Auditoría
    creado_en               TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tipo_usuario CHECK (tipo_usuario IN (
        'admin', 'evaluador', 'supervisor', 'vendedor', 'gerente'
    ))
);

CREATE INDEX idx_usuario_email ON usuario(email);
CREATE INDEX idx_usuario_tipo ON usuario(tipo_usuario);
```

### 4.2 configuracion_sistema

```sql
CREATE TABLE configuracion_sistema (
    clave                   VARCHAR(100) PRIMARY KEY,
    valor                   JSONB NOT NULL,
    descripcion             TEXT,
    grupo                   VARCHAR(50),
    editable                BOOLEAN DEFAULT true,
    actualizado_en          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    actualizado_por         VARCHAR(100)
);

-- Insertar configuraciones por defecto
INSERT INTO configuracion_sistema (clave, valor, descripcion, grupo) VALUES
('SCORE_MINIMO_APROBACION', '{"valor": 65}', 'Score mínimo para aprobar', 'SCORES'),
('SCORE_MINIMO_CONDICIONAL', '{"valor": 50}', 'Score mínimo para aprobación condicional', 'SCORES'),
('PESO_SCORE_DOCUMENTAL', '{"valor": 0.20}', 'Peso del score documental en score final', 'SCORES'),
('PESO_SCORE_REFERENCIAS', '{"valor": 0.15}', 'Peso del score de referencias en score final', 'SCORES'),
('PESO_SCORE_CREDITICIO', '{"valor": 0.25}', 'Peso del score crediticio en score final', 'SCORES'),
('PESO_SCORE_INGRESOS', '{"valor": 0.20}', 'Peso del score de ingresos en score final', 'SCORES'),
('PESO_SCORE_ENTREVISTA', '{"valor": 0.20}', 'Peso del score de entrevista en score final', 'SCORES'),
('RELACION_CUOTA_INGRESO_MAXIMA', '{"valor": 0.30}', 'Relación máxima cuota/ingreso permitida', 'FINANCIAMIENTO'),
('INICIAL_MINIMA_PORCENTAJE', '{"valor": 0.20}', 'Porcentaje mínimo de inicial', 'FINANCIAMIENTO');
```

---

## 5. TRIGGERS Y FUNCIONES

### 5.1 Trigger para actualizar timestamps

```sql
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Aplicar a todas las tablas con actualizado_en
CREATE TRIGGER trg_evaluacion_timestamp
    BEFORE UPDATE ON evaluacion
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_persona_timestamp
    BEFORE UPDATE ON persona
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_documento_timestamp
    BEFORE UPDATE ON documento
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_referencia_timestamp
    BEFORE UPDATE ON referencia
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_financiamiento_timestamp
    BEFORE UPDATE ON financiamiento
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_entrevista_timestamp
    BEFORE UPDATE ON entrevista
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();
```

### 5.2 Trigger para generar número de evaluación

```sql
CREATE SEQUENCE seq_numero_evaluacion START 1;

CREATE OR REPLACE FUNCTION generar_numero_evaluacion()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.numero_evaluacion IS NULL THEN
        NEW.numero_evaluacion := 'EV-' ||
            TO_CHAR(CURRENT_DATE, 'YYYY') || '-' ||
            LPAD(nextval('seq_numero_evaluacion')::TEXT, 5, '0');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_generar_numero_evaluacion
    BEFORE INSERT ON evaluacion
    FOR EACH ROW EXECUTE FUNCTION generar_numero_evaluacion();
```

### 5.3 Trigger para insertar evento automáticamente

```sql
CREATE OR REPLACE FUNCTION registrar_evento_cambio_estado()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.estado IS DISTINCT FROM NEW.estado THEN
        INSERT INTO evaluacion_evento (
            evaluacion_id,
            tipo_evento,
            payload,
            version,
            usuario_id,
            usuario_nombre
        ) VALUES (
            NEW.id,
            'ESTADO_CAMBIADO',
            jsonb_build_object(
                'estadoAnterior', OLD.estado,
                'estadoNuevo', NEW.estado
            ),
            NEW.version,
            COALESCE(NEW.actualizado_por, 'SYSTEM'),
            'Sistema'
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evento_cambio_estado
    AFTER UPDATE ON evaluacion
    FOR EACH ROW EXECUTE FUNCTION registrar_evento_cambio_estado();
```

### 5.4 Función para refrescar vistas materializadas

```sql
CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY evaluacion_lista_mv;
    REFRESH MATERIALIZED VIEW CONCURRENTLY dashboard_estadisticas_mv;
    REFRESH MATERIALIZED VIEW CONCURRENTLY estadisticas_por_tienda_mv;
END;
$$ LANGUAGE plpgsql;

-- Crear job para refrescar cada 5 minutos (usando pg_cron si está disponible)
-- SELECT cron.schedule('refresh-mvs', '*/5 * * * *', 'SELECT refresh_all_materialized_views()');
```

---

## 6. DIAGRAMA ER

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│    PERSONA      │       │   EVALUACION    │       │    VEHICULO     │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id (PK)         │◄──────│ titular_id (FK) │       │ id (PK)         │
│ tipo            │       │ fiador_id (FK)  │───────│ marca           │
│ nombres         │       │ vehiculo_id(FK) │◄──────│ modelo          │
│ apellidos       │       │                 │       │ anio            │
│ documento       │       │ id (PK)         │       │ precio          │
│ contacto        │       │ numero_eval     │       └─────────────────┘
│ direccion       │       │ estado          │
│ trabajo         │       │ etapa           │       ┌─────────────────┐
│ archivos (JSON) │       │ prioridad       │       │ FINANCIAMIENTO  │
└─────────────────┘       │ progreso        │       ├─────────────────┤
                          │ scores          │       │ id (PK)         │
┌─────────────────┐       │ decision        │◄──────│ evaluacion_id   │
│   DOCUMENTO     │       │ tienda_info     │       │ monto_vehiculo  │
├─────────────────┤       │ vendedor_info   │       │ inicial         │
│ id (PK)         │       │ timestamps      │       │ financiar       │
│ evaluacion_id   │───────│                 │       │ cuotas          │
│ tipo            │       └────────┬────────┘       └─────────────────┘
│ tipo_persona    │                │
│ estado          │                │                ┌─────────────────┐
│ url             │                │                │   REFERENCIA    │
│ validado        │                │                ├─────────────────┤
└─────────────────┘                │                │ id (PK)         │
                                   │                │ evaluacion_id   │───┐
┌─────────────────┐                │                │ numero          │   │
│   ENTREVISTA    │                │                │ nombre          │   │
├─────────────────┤                │                │ telefono        │   │
│ id (PK)         │                │                │ verificada      │   │
│ evaluacion_id   │────────────────┤                │ resultado       │   │
│ tipo_persona    │                │                └─────────────────┘   │
│ scores (1-5)    │                │                                      │
│ recomendacion   │                │                ┌─────────────────┐   │
└─────────────────┘                │                │     ALERTA      │   │
                                   │                ├─────────────────┤   │
┌─────────────────┐                │                │ id (PK)         │   │
│ ETAPA_EVALUACION│                │                │ evaluacion_id   │───┤
├─────────────────┤                ├────────────────│ tipo            │   │
│ id (PK)         │                │                │ severidad       │   │
│ evaluacion_id   │────────────────┤                │ mensaje         │   │
│ numero          │                │                │ resuelta        │   │
│ nombre          │                │                └─────────────────┘   │
│ estado          │                │                                      │
└─────────────────┘                │                                      │
                                   │                                      │
                    ┌──────────────┴──────────────┐                       │
                    │     EVALUACION_EVENTO       │                       │
                    │      (EVENT STORE)          │                       │
                    ├─────────────────────────────┤                       │
                    │ id (PK)                     │◄──────────────────────┘
                    │ evaluacion_id (FK)          │
                    │ tipo_evento                 │
                    │ payload (JSONB)             │
                    │ version                     │
                    │ usuario_id                  │
                    │ timestamp                   │
                    │ [APPEND-ONLY]               │
                    └─────────────────────────────┘
```

---

## 7. ORDEN DE CREACIÓN

```sql
-- 1. Tablas independientes primero
CREATE TABLE vehiculo (...);
CREATE TABLE persona (...);
CREATE TABLE usuario (...);
CREATE TABLE configuracion_sistema (...);

-- 2. Tabla principal (sin FKs temporalmente)
CREATE TABLE evaluacion (...);

-- 3. Tabla de financiamiento
CREATE TABLE financiamiento (...);

-- 4. Agregar FK de evaluacion a financiamiento
ALTER TABLE evaluacion ADD CONSTRAINT fk_evaluacion_financiamiento
    FOREIGN KEY (financiamiento_id) REFERENCES financiamiento(id);

-- 5. Tablas dependientes de evaluacion
CREATE TABLE documento (...);
CREATE TABLE referencia (...);
CREATE TABLE entrevista (...);
CREATE TABLE alerta (...);
CREATE TABLE etapa_evaluacion (...);

-- 6. Event Store
CREATE TABLE evaluacion_evento (...);

-- 7. Vistas materializadas
CREATE MATERIALIZED VIEW evaluacion_lista_mv AS ...;
CREATE MATERIALIZED VIEW dashboard_estadisticas_mv AS ...;
CREATE MATERIALIZED VIEW estadisticas_por_tienda_mv AS ...;

-- 8. Triggers y funciones
CREATE FUNCTION update_timestamp() ...;
CREATE TRIGGER trg_evaluacion_timestamp ...;
-- etc.
```

---

## 8. CONSIDERACIONES DE ESCALABILIDAD

| Aspecto | Solución |
|---------|----------|
| **Particionamiento** | Particionar `evaluacion_evento` por fecha (mensual) |
| **Índices** | Usar índices parciales para estados activos |
| **Cache** | Redis para `evaluacion_lista_mv` con TTL de 5 min |
| **Archiving** | Mover evaluaciones > 2 años a tablas de archivo |
| **Read Replicas** | Usar réplicas para consultas de lista/dashboard |
| **Connection Pooling** | PgBouncer para manejar conexiones |

```sql
-- Ejemplo de particionamiento por fecha
CREATE TABLE evaluacion_evento (
    ...
) PARTITION BY RANGE (timestamp);

CREATE TABLE evaluacion_evento_2026_01
    PARTITION OF evaluacion_evento
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE evaluacion_evento_2026_02
    PARTITION OF evaluacion_evento
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
```

---

Este modelo soporta:
- Event Sourcing completo
- Auditoría total
- Consultas rápidas con vistas materializadas
- Escalabilidad horizontal
- Integridad referencial
- Control de concurrencia optimista
