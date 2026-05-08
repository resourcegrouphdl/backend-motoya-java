# Manual de Usuario — Módulo de Contabilidad
### Motoya v2 · Panel Financiero

---

## Índice

1. [¿Para qué sirve este módulo?](#1-para-qué-sirve-este-módulo)
2. [¿Quién debe usar esta pantalla?](#2-quién-debe-usar-esta-pantalla)
3. [Panel Principal — visión general](#3-panel-principal--visión-general)
4. [Barra de herramientas](#4-barra-de-herramientas)
5. [KPIs superiores — Cartera e IGV](#5-kpis-superiores--cartera-e-igv)
6. [Utilidad del Mes — el ledger contable](#6-utilidad-del-mes--el-ledger-contable)
7. [Aging de Cartera](#7-aging-de-cartera)
8. [Flujo de Caja Proyectado](#8-flujo-de-caja-proyectado)
9. [Navegación a sub-módulos](#9-navegación-a-sub-módulos)
10. [¿Cómo y cuándo se actualiza la información?](#10-cómo-y-cuándo-se-actualiza-la-información)
11. [Preguntas frecuentes](#11-preguntas-frecuentes)
12. [Glosario](#12-glosario)

---

## 1. ¿Para qué sirve este módulo?

El módulo de contabilidad de Motoya centraliza la información financiera del negocio de créditos vehiculares. Permite responder, en tiempo real o near-real-time, preguntas como:

- ¿Cuánto dinero tengo en cartera activa?
- ¿Cuánto cobré este mes y de ese cobro, cuánto es ganancia real?
- ¿Cuánto le pagué a las tiendas este mes?
- ¿Cuántos clientes están en mora y por cuánto?
- ¿Qué ingresos puedo esperar en los próximos 6 meses?

> **Importante:** este módulo es de **solo lectura**. No modifica contratos, cobros ni facturas. Consolida y presenta información que ya existe en los demás módulos del sistema.

---

## 2. ¿Quién debe usar esta pantalla?

| Perfil | Uso principal |
|---|---|
| **Gerente / Administrador** | Revisión diaria de KPIs, utilidad neta del período, salud de cartera |
| **Contador** | Seguimiento de IGV emitido, costos por tienda, utilidad para cierre mensual |
| **Jefe de cobranzas** | Monitoreo del aging (mora), priorización de gestión |
| **Analista financiero** | Flujo de caja proyectado, desglose por quincena |

---

## 3. Panel Principal — visión general

Al ingresar al módulo (`/contabilidad`) se carga el **Panel Principal**, que se divide en cinco zonas:

```
┌─────────────────────────────────────────────────────────┐
│  BARRA  │  Contabilidad — Panel Principal  │ Sincronizar │
├──────────┬──────────┬──────────┬───────────────────────┤
│ Cartera  │  Mora    │  Ventas  │      IGV Emitido       │
│  Total   │  Total   │  del Mes │                        │
├──────────┴──────────┴──────────┴───────────────────────┤
│  Interés  │  Capital   │  Pagos a   │   Utilidad Neta   │
│  Ganado   │ Recuperado │  Tiendas   │   (verde/rojo)    │
├─────────────────────────┬───────────────────────────────┤
│   AGING DE CARTERA      │   FLUJO DE CAJA PROYECTADO    │
│   (tabla por tramos)    │   (próximos 6 meses)          │
├─────────────────────────┴───────────────────────────────┤
│  Comprobantes │ Cartera & Aging │ Recaudación │ Análisis │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Barra de herramientas

En la parte superior de la pantalla hay dos controles:

### Botón Sincronizar

```
[ ↻ Sincronizar ]
```

- **Qué hace:** dispara el recálculo completo del ledger contable. El sistema lee todos los cobros aprobados, pagos a tiendas y comisiones, y actualiza los registros internos de contabilidad.
- **Cuándo usarlo:** cuando se quiera ver datos más recientes sin esperar el ciclo automático de 6 horas, o justo después de haber registrado pagos o cobros importantes.
- **Duración:** entre 5 y 30 segundos dependiendo del volumen de operaciones.
- **Indicador:** mientras el proceso corre, el botón muestra "Sincronizando…" y queda desactivado. Al terminar, aparece un mensaje de confirmación en la parte inferior de la pantalla.

### Botón Actualizar (ícono ↺)

- **Qué hace:** recarga los datos que ya están en la base de datos y los muestra en el panel. **No** ejecuta el recálculo del ledger.
- **Cuándo usarlo:** para refrescar la vista después de que otra persona haya sincronizado, o simplemente para asegurarse de ver la última información disponible.

---

## 5. KPIs superiores — Cartera e IGV

Esta fila de cuatro tarjetas muestra indicadores que **se leen directamente de los datos operativos** (contratos y comprobantes SUNAT) sin pasar por el ledger contable.

---

### 5.1 Cartera Total

| | |
|---|---|
| **Ícono** | Billetera azul |
| **Unidad** | Soles (S/) |
| **Fuente** | Colección `contratos` |

**¿Qué mide?**
La suma de todos los saldos pendientes de los contratos activos. Es decir, cuánto dinero tienen que devolver en total todos los clientes con crédito vigente.

**¿Cómo se calcula?**
Para cada contrato activo: `saldoPendiente = montoFinanciado × (1 + tasa) − total ya cobrado`.

**¿Para qué sirve?**
- Conocer la exposición total del negocio.
- Comparar semana a semana si la cartera crece (más créditos nuevos) o decrece (más cobros que nuevos contratos).

**Subtítulo:** número de contratos activos incluidos en el cálculo.

---

### 5.2 Total Mora

| | |
|---|---|
| **Ícono** | Triángulo de alerta naranja |
| **Unidad** | Soles (S/) |
| **Fuente** | Colección `contratos` |

**¿Qué mide?**
El monto acumulado de cuotas vencidas que aún no han sido pagadas.

**¿Cómo se calcula?**
Cuotas con `fechaVencimiento < hoy` y que no tienen voucher de pago aprobado asociado.

**¿Para qué sirve?**
- Medir el riesgo de la cartera.
- El subtítulo muestra el **porcentaje de recuperación** (`total cobrado / total a cobrar × 100`). Un porcentaje alto indica buena gestión de cobranzas.

> **Ejemplo:** si la cartera es S/ 500,000 y la mora es S/ 25,000, el negocio tiene un 95% de recuperación — considerado saludable para microfinanzas.

---

### 5.3 Ventas del Mes

| | |
|---|---|
| **Ícono** | Recibo morado |
| **Unidad** | Soles (S/) |
| **Fuente** | Colección `comprobantes` (SUNAT) |

**¿Qué mide?**
El total bruto (subtotal + IGV) de todos los comprobantes emitidos en el mes calendario actual.

**¿Cómo se calcula?**
Suma de `total` de todas las boletas y facturas con `fechaEmision >= primer día del mes`.

**¿Para qué sirve?**
- Ver el volumen comercial del mes en términos fiscales.
- Monitorear si la operación está creciendo respecto a meses anteriores.

**Subtítulo:** total de comprobantes emitidos (boletas + facturas).

> **Nota:** esta cifra representa ventas registradas con comprobante SUNAT. No es lo mismo que los cobros de cuotas — son las operaciones de venta nuevas.

---

### 5.4 IGV Emitido

| | |
|---|---|
| **Ícono** | Banco teal |
| **Unidad** | Soles (S/) |
| **Fuente** | Colección `comprobantes` (SUNAT) |

**¿Qué mide?**
La suma del IGV (18%) incluido en todos los comprobantes del mes.

**¿Cómo se calcula?**
Suma del campo `igv` de cada comprobante del período.

**¿Para qué sirve?**
- Seguimiento de la obligación tributaria mensual.
- Insumo directo para la declaración PDT 621 ante SUNAT.

**Subtítulo:** muestra el subtotal (base imponible) sobre el que se calculó ese IGV.

---

## 6. Utilidad del Mes — el ledger contable

Esta sección **solo aparece cuando el sistema tiene datos en el ledger contable** (`contabilidad_movimientos`). Si es la primera vez que se usa el módulo, esta sección estará vacía hasta ejecutar la primera sincronización.

Las cuatro tarjetas de esta sección responden la pregunta central del negocio: **¿cuánto ganamos realmente este mes?**

---

### 6.1 Interés Ganado

| | |
|---|---|
| **Ícono** | Tendencia hacia arriba, verde |
| **Unidad** | Soles (S/) |
| **Fuente** | Ledger `contabilidad_movimientos`, tipo `INGRESO_CUOTA` |

**¿Qué mide?**
De todos los cobros recibidos este mes, la porción que corresponde a **interés** — la ganancia financiera del negocio.

**¿Cómo se calcula?**
Cuando el sistema procesa un voucher de pago aprobado, consulta el contrato para conocer la tasa pactada y desglosa cada pago:

```
interésPorCuota = montoFinanciado × tasa / numeroCuotas
capitalPorCuota = montoFinanciado / numeroCuotas
```

La suma de todos los `interésPorCuota` del mes es el **Interés Ganado**.

**¿Para qué sirve?**
Es el indicador más importante del negocio financiero. Representa el **ingreso neto por la actividad de financiamiento**, independientemente del dinero que se "mueve" por capital.

**Subtítulo:** cantidad de cobros individuales procesados en el mes.

---

### 6.2 Capital Recuperado

| | |
|---|---|
| **Ícono** | Pagos, azul |
| **Unidad** | Soles (S/) |
| **Fuente** | Ledger `contabilidad_movimientos`, tipo `INGRESO_CUOTA` |

**¿Qué mide?**
La porción de los cobros del mes que corresponde a **devolución del capital prestado** — no es ganancia, es recuperación de la inversión.

**¿Para qué sirve?**
- Medir la velocidad de recuperación del capital.
- Junto con el Interés Ganado, permite ver la composición completa de los cobros.

**Subtítulo:** total cobrado bruto del que se extrae ese capital (interés + capital juntos).

> **Ejemplo de lectura:** si se cobró S/ 10,000 este mes, de los cuales S/ 2,630 son interés y S/ 7,370 son capital — el negocio ganó S/ 2,630 y recuperó S/ 7,370 de inversión.

---

### 6.3 Pagos a Tiendas

| | |
|---|---|
| **Ícono** | Tienda, naranja |
| **Borde** | Rojo (es un costo) |
| **Unidad** | Soles (S/) |
| **Fuente** | Ledger `contabilidad_movimientos`, tipo `COSTO_TIENDA` |

**¿Qué mide?**
El total pagado a las tiendas proveedoras por los vehículos financiados en el mes.

**¿Cómo se captura?**
El sistema lee la colección `finanzas_facturas` y sus sub-registros de pagos con `estado = PAGADO`. Cada pago genera una entrada en el ledger como `COSTO_TIENDA`.

**¿Para qué sirve?**
Es el **principal costo del negocio** — el dinero que Motoya desembolsa para adquirir los vehículos que luego financia. Esta cifra es clave para determinar si el margen de interés es suficiente para cubrir los costos operativos.

**Subtítulo:** número de facturas pagadas a tiendas en el mes.

---

### 6.4 Utilidad Neta

| | |
|---|---|
| **Ícono** | Banco, verde (positivo) / rojo (negativo) |
| **Borde** | Verde si positiva, rojo si negativa |
| **Unidad** | Soles (S/) |
| **Fuente** | Calculada sobre el ledger |

**¿Qué mide?**
El resultado financiero final del mes.

**Fórmula:**
```
Utilidad Neta = Interés Ganado − Pagos a Tiendas − Comisiones Pagadas
```

**¿Para qué sirve?**
Indica si el negocio está siendo rentable. La tarjeta cambia de color como señal visual:
- **Verde:** el mes es rentable.
- **Rojo:** los costos superaron los ingresos de interés — requiere revisión.

**Subtítulo:** margen neto en porcentaje (`Utilidad Neta / Total Cobrado × 100`).

> **Punto de referencia:** un margen neto saludable en microfinanzas vehiculares suele estar entre 15% y 30%. Por debajo del 10% se recomienda revisar los términos de las tasas o los costos de tienda.

---

## 7. Aging de Cartera

La tabla de aging muestra **cómo está distribuida la cartera según los días de mora** de cada cliente.

### Columnas de la tabla

| Columna | Descripción |
|---|---|
| **Bucket** | Tramo de mora con etiqueta y color |
| **Contratos** | Cantidad de contratos en ese tramo |
| **Saldo (S/)** | Monto pendiente total en ese tramo |
| **%** | Proporción del tramo sobre el total (barra visual) |

### Tramos y su significado

| Color | Tramo | Días de mora | Acción recomendada |
|---|---|---|---|
| Verde | AL DÍA | 0 | Ninguna — clientes al corriente |
| Naranja | 1–30 días | 1 a 30 | Recordatorio amigable, llamada preventiva |
| Naranja oscuro | 31–60 días | 31 a 60 | Gestión activa de cobranza, visita o llamada formal |
| Rojo | 61–90 días | 61 a 90 | Gestión intensiva, posible acuerdo de pago |
| Morado | +90 días | Más de 90 | Evaluación de mora crítica, acciones legales o castigo contable |

### De dónde sale

La colección `contratos` contiene el cronograma de cuotas de cada cliente. El sistema calcula `hoy − fechaVencimientoUltimaCuotaImpaga` para ubicar cada contrato en su tramo.

### Para qué sirve

- **Priorizar la cobranza:** los tramos rojos y morado necesitan atención urgente.
- **Calcular provisiones:** contablemente, los tramos de mayor mora requieren reservas de incobrables.
- **Detectar tendencias:** si semana a semana el tramo 31–60 crece, hay un problema de cobranza que viene de 4–8 semanas atrás.

---

## 8. Flujo de Caja Proyectado

### Qué muestra

Una proyección de los **ingresos esperados en los próximos 6 meses** basada en los contratos activos y sus cuotas pendientes.

### Cómo se lee

Cada fila es un mes con una barra proporcional:

```
Mayo 2025        ████████████████████  S/ 48,200
Junio 2025       ████████████████      S/ 39,600
Julio 2025       █████████████         S/ 32,100
Agosto 2025      ████████              S/ 21,800
Septiembre 2025  ██████                S/ 16,400
Octubre 2025     ████                  S/ 10,900
```

La barra más larga representa el 100% (el mes con más ingresos esperados), las demás se escalan proporcionalmente.

### De dónde sale

El sistema lee todos los contratos activos en `contratos` y para cada uno proyecta las cuotas pendientes distribuyéndolas en el tiempo según su `fechaVencimiento`.

### Para qué sirve

- **Planificación de liquidez:** saber con antelación qué dinero entra permite planificar pagos a tiendas y comisiones.
- **Detectar meses débiles:** si agosto aparece muy bajo, se puede planificar adelantar gestiones de cobranza o diferir gastos.
- **Tomar decisiones de nuevos créditos:** si hay capacidad de flujo holgada, se pueden aprobar más solicitudes.

> **Limitación a tener en cuenta:** esta es una proyección basada en contratos activos y asume que todos pagarán. La realidad puede diferir según la mora. Usar siempre junto con el aging para tener el panorama completo.

---

## 9. Navegación a sub-módulos

Al pie del panel hay cuatro accesos directos a pantallas de mayor detalle:

### Comprobantes SUNAT (`/contabilidad/comprobantes`)
Lista completa de boletas y facturas emitidas. Permite filtrar por fecha, tienda y tipo de comprobante. Útil para el contador al preparar declaraciones.

### Cartera & Aging (`/contabilidad/cartera`)
Vista detallada contrato por contrato, con información del cliente, saldo, cuotas pagadas y días de mora. Útil para el equipo de cobranzas.

### Recaudación (`/contabilidad/recaudacion`)
Historial de cobros recibidos agrupados por período (semana, quincena, mes). Permite ver la evolución de los ingresos en el tiempo.

### Análisis de Vouchers (`/contabilidad/analisis`)
Dos vistas especializadas:
- **Discrepancias OCR:** vouchers donde el monto detectado por reconocimiento de imagen difiere del monto esperado según el contrato (diferencia superior a S/ 0.50). Útil para detectar errores de captura o intentos de fraude.
- **Concentración bancaria:** qué bancos concentran más pagos. Útil para negociar convenios o detectar patrones de pago de los clientes.

---

## 10. ¿Cómo y cuándo se actualiza la información?

El módulo tiene **dos tipos de datos** con mecanismos de actualización distintos:

### Datos operativos (Cartera, IGV, Aging, Flujo de Caja)

Se consultan directamente de las colecciones de negocio (`contratos`, `comprobantes`) **en el momento en que se carga la pantalla**. Siempre reflejan el estado actual.

Para refrescarlos basta con presionar el botón **Actualizar** (ícono ↺).

### Datos del ledger contable (Utilidad del Mes)

Estos datos son calculados y almacenados por un proceso automático que corre **cada 6 horas**. El flujo es:

```
[Cada 6 horas — automático]
          │
          ▼
  Lee cobranzas-vouchers     → estado APROBADO
  Lee finanzas_facturas       → pagos estado PAGADO
  Lee pagos_comisiones         → estado PAGADO
          │
          ▼
  Calcula desglose capital/interés por contrato
  usando la tasa pactada en cada contrato
          │
          ▼
  Escribe en contabilidad_movimientos
  (si ya existe el registro, lo omite — nunca duplica)
          │
          ▼
  Panel muestra los datos actualizados
```

**Horarios aproximados de actualización automática:**
00:00, 06:00, 12:00, 18:00 (hora Lima).

Para actualización inmediata fuera de esos horarios → botón **Sincronizar**.

---

## 11. Preguntas frecuentes

**¿Por qué la sección "Utilidad del Mes" está vacía?**
El ledger contable aún no tiene datos. Presionar el botón **Sincronizar** para generar el primer cálculo histórico. Puede tomar hasta un minuto la primera vez.

**¿Por qué el "Interés Ganado" no coincide exactamente con lo que esperaría?**
El sistema calcula el interés de forma proporcional por cuota usando la tasa del contrato. Si un cliente pagó más o menos de una cuota exacta, el desglose refleja la proporción matemática, no necesariamente el importe acordado en el cronograma.

**¿Puedo filtrar por tienda/sucursal?**
El panel principal muestra datos consolidados de todas las tiendas. Los sub-módulos (Comprobantes, Cartera, Recaudación, Análisis) sí permiten filtrar por tienda.

**¿Con qué frecuencia debo revisar este panel?**
- **Cartera, Mora y Aging:** al menos una vez al día hábil.
- **Utilidad del Mes:** semanalmente o al cierre del mes.
- **Flujo de Caja:** una vez por semana para planificación.

**¿Quién puede presionar el botón Sincronizar?**
Cualquier usuario con acceso al módulo de contabilidad. La operación es segura — si se ejecuta varias veces, no genera datos duplicados.

**¿Los datos del módulo afectan los contratos o cobros registrados?**
No. El módulo de contabilidad es de solo lectura sobre los datos operativos. Únicamente escribe en las colecciones propias (`contabilidad_movimientos`, `contabilidad_cuotas`) que son internas al módulo.

---

## 12. Glosario

| Término | Definición |
|---|---|
| **Cartera activa** | Conjunto de contratos de crédito vigentes con saldo pendiente |
| **Mora** | Días transcurridos desde el vencimiento de una cuota no pagada |
| **Aging** | Análisis de la cartera distribuida por tramos de días de mora |
| **Ledger contable** | Registro append-only de todos los movimientos financieros, clasificados por tipo |
| **INGRESO_CUOTA** | Movimiento en el ledger que representa un cobro de cuota aprobado |
| **COSTO_TIENDA** | Movimiento en el ledger que representa un pago realizado a una tienda proveedora |
| **COSTO_COMISION** | Movimiento en el ledger que representa una comisión pagada a un vendedor |
| **Capital** | Monto original prestado al cliente, sin incluir intereses |
| **Interés** | Ganancia del negocio por el servicio de financiamiento |
| **Utilidad Neta** | Resultado final: ingresos por interés menos todos los costos del período |
| **Margen Neto** | Utilidad Neta expresada como porcentaje del total cobrado |
| **Flujo de caja** | Proyección de los ingresos esperados en períodos futuros |
| **Comprobante SUNAT** | Documento fiscal (boleta o factura) emitido en cada operación de venta |
| **IGV** | Impuesto General a las Ventas — 18% sobre el subtotal de cada comprobante |
| **Sincronización** | Proceso que lee los datos operativos y actualiza el ledger contable |
| **Idempotente** | Propiedad del sistema que garantiza que ejecutar el proceso múltiples veces no genera duplicados |
| **OCR** | Reconocimiento óptico de caracteres — tecnología usada para leer los montos de los vouchers de pago fotografiados |

---

*Documento generado para uso interno de Motoya v2 — Módulo Contabilidad*
*Versión: 1.0 — Mayo 2025*
