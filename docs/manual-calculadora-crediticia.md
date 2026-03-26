# Manual de Usuario — Calculadora Crediticia Motoya

**Versión:** 2.0
**Fecha:** Marzo 2026
**Módulo:** `/calculadora`
**Normativa aplicable:** SBS Perú (Res. N° 11356-2008, Ley 26702, Circular B-2216-2010)

---

## Índice

1. [¿Qué es la Calculadora Crediticia?](#1-qué-es-la-calculadora-crediticia)
2. [¿A quién está dirigida?](#2-a-quién-está-dirigida)
3. [Acceso al módulo](#3-acceso-al-módulo)
4. [Pantalla: Simulador de Crédito](#4-pantalla-simulador-de-crédito)
   - 4.1 [Frecuencia de pago](#41-frecuencia-de-pago)
   - 4.2 [Precio del vehículo y SOAT](#42-precio-del-vehículo-y-soat)
   - 4.3 [Inicial del cliente](#43-inicial-del-cliente)
   - 4.4 [Plazo y número de cuotas](#44-plazo-y-número-de-cuotas)
   - 4.5 [Comisión de desembolso](#45-comisión-de-desembolso)
   - 4.6 [TEA personalizada](#46-tea-personalizada)
   - 4.7 [Seguro de desgravamen](#47-seguro-de-desgravamen)
5. [Resultados de la simulación](#5-resultados-de-la-simulación)
   - 5.1 [Tarjetas KPI](#51-tarjetas-kpi)
   - 5.2 [Detalle del financiamiento](#52-detalle-del-financiamiento)
   - 5.3 [Cronograma de pagos](#53-cronograma-de-pagos)
6. [Pantalla: Configuración Crediticia](#6-pantalla-configuración-crediticia)
   - 6.1 [Gastos y límites](#61-gastos-y-límites-de-financiamiento)
   - 6.2 [Tasas de interés y seguro](#62-tasas-de-interés-y-seguro)
   - 6.3 [Comisión por defecto](#63-comisión-por-defecto)
   - 6.4 [Plazos disponibles](#64-plazos-disponibles)
7. [Glosario financiero](#7-glosario-financiero)
8. [Preguntas frecuentes](#8-preguntas-frecuentes)

---

## 1. ¿Qué es la Calculadora Crediticia?

La Calculadora Crediticia de Motoya es una herramienta administrativa que permite **simular créditos para la compra de vehículos** (motos) con total transparencia financiera, cumpliendo con los estándares de la Superintendencia de Banca, Seguros y AFP del Perú (SBS).

Permite conocer con exactitud:
- El monto de la cuota (semanal o mensual)
- La tasa efectiva real que paga el cliente (TCEA)
- El desglose completo de cada pago: capital, interés y seguro
- El cronograma completo de pagos

El sistema utiliza el **Sistema Francés de amortización**, que garantiza una cuota fija durante todo el plazo del crédito — lo que facilita la planificación financiera del cliente.

---

## 2. ¿A quién está dirigida?

| Perfil | Uso principal |
|---|---|
| **Asesor comercial** | Simular créditos ante el cliente, comparar escenarios de plazo e inicial |
| **Administrador financiero** | Configurar TEA, plazos, gastos y comisiones |
| **Analista de créditos** | Verificar condiciones antes de aprobar una solicitud |
| **Gerencia** | Supervisar parámetros crediticios y costos totales |

---

## 3. Acceso al módulo

Desde el panel administrativo, navegar al menú lateral:

```
Panel Admin → Calculadora → Simulador
                          → Configuración
```

Ambas pantallas requieren sesión activa con cuenta Firebase. El botón **"Configurar parámetros"** en la pantalla del simulador lleva directamente a la configuración, y viceversa.

---

## 4. Pantalla: Simulador de Crédito

Esta es la pantalla principal. Los resultados se recalculan **automáticamente** cada vez que se modifica cualquier campo (con un retardo de 600ms para evitar llamadas innecesarias). También existe el botón **"Calcular"** para forzar el recálculo de forma manual.

### 4.1 Frecuencia de pago

**Campo:** Selector desplegable
**Opciones:** `Mensual` | `Semanal (cada 7 días)`

Define cada cuánto tiempo el cliente realizará sus pagos. **Esta es la primera decisión a tomar** porque afecta el número de cuotas y los plazos disponibles en el panel de selección rápida.

| Frecuencia | Descripción | Cuándo usarla |
|---|---|---|
| **Mensual** | 1 pago por mes, en la misma fecha | Clientes con ingresos mensuales (sueldo fijo, negocio mensual) |
| **Semanal** | 1 pago cada 7 días exactos | Clientes con ingresos semanales (mercado, transporte, servicios) |

> **Importante:** Los plazos mostrados en los chips de selección rápida cambian automáticamente según la frecuencia elegida. Los plazos mensuales se muestran en meses; los semanales, en semanas.

---

### 4.2 Precio del vehículo y SOAT

**Campo Precio del vehículo:** Monto en soles del vehículo (mínimo S/ 500)
**Campo SOAT:** Costo del SOAT en soles (por defecto 0)

Ambos son **costos financiables**: se suman para formar el **capital base** del crédito.

```
Capital base = Precio del vehículo + SOAT + Gastos administrativos
```

El capital base es la referencia para calcular la inicial mínima y el monto a financiar. Al ingresar el SOAT como parte del financiamiento, el cliente no necesita pagarlo por separado al momento de la compra.

> **Ejemplo:**
> Precio moto: S/ 3,500
> SOAT: S/ 250
> Gastos admin: S/ 890
> **Capital base: S/ 4,640**

---

### 4.3 Inicial del cliente

**Campo:** Monto en soles (opcional)

La inicial es el pago que el cliente realiza de forma inmediata al momento de adquirir el vehículo. Su propósito es **reducir el monto a financiar**.

**Reglas del sistema:**

1. Si se deja en blanco, el sistema calcula y aplica automáticamente la **inicial mínima** (porcentaje configurado sobre el capital base).
2. Si el cliente ingresa una inicial mayor a la mínima, se respeta.
3. Si la inicial ingresada es menor a la mínima, el sistema la reemplaza por la mínima sin error — simplemente aplica la mínima.
4. Si el monto a financiar supera el **tope máximo configurado**, la inicial se ajusta automáticamente hacia arriba para encuadrar el crédito. En ese caso, aparece un aviso en pantalla.

> **Consejo:** Dejar en blanco para ver el escenario base. Luego ingresar el monto real de la inicial del cliente para obtener la simulación definitiva.

---

### 4.4 Plazo y número de cuotas

**Selección rápida:** Chips de botones con los plazos preconfigurados
**Campo manual:** Número de cuotas (semanas o meses según la frecuencia elegida)

Los chips de selección rápida muestran los plazos configurados en el sistema para la frecuencia activa. Al hacer clic en un chip, el campo numérico se actualiza automáticamente.

**¿Cómo se determina la TEA según el plazo?**

Cada plazo preconfigurado tiene su propia TEA (tasa de interés). Si el número de cuotas ingresado manualmente no coincide con ningún plazo preconfigurado, el sistema aplica la **TEA por defecto** definida en la configuración.

| Escenario | TEA aplicada |
|---|---|
| El plazo coincide con uno preconfigurado | TEA específica de ese plazo |
| El plazo no coincide con ninguno | TEA por defecto (configuración global) |
| Se usa TEA personalizada (override) | La TEA ingresada manualmente |

---

### 4.5 Comisión de desembolso

**Toggle:** "Incluir comisión de desembolso"
**Campos adicionales (al activar):** Tipo, Valor, Financiar comisión

La comisión es un cargo adicional al crédito. Su configuración tiene implicancias importantes en la TCEA y en lo que el cliente efectivamente recibe.

#### Tipo de comisión

| Tipo | Descripción | Ejemplo |
|---|---|---|
| **Monto fijo (S/)** | Cargo fijo en soles, independiente del monto | S/ 150 siempre |
| **Porcentaje del capital (%)** | Porcentaje calculado sobre el capital base | 2% de S/ 4,640 = S/ 92.80 |

#### ¿Financiar la comisión?

Esta es la decisión más importante en cuanto a transparencia SBS:

**Comisión FINANCIADA (toggle activado):**
- La comisión se suma al capital del préstamo
- El cliente la paga en cuotas, no al momento del desembolso
- Las cuotas serán ligeramente mayores
- La TCEA resultante es cercana a la TEA

**Comisión NO FINANCIADA (toggle desactivado):**
- La comisión se cobra al momento del desembolso
- El cliente recibe menos efectivo del que figura como préstamo
- Afecta directamente la TCEA (la eleva considerablemente)
- Es la modalidad que exige mayor transparencia según SBS

> **Ejemplo concreto:**
> Préstamo: S/ 3,700 · Comisión: S/ 150
>
> **Financiada:** El cliente recibe S/ 3,700. El préstamo sube a S/ 3,850. Cuotas mayores.
> **No financiada:** El cliente recibe S/ 3,550 (S/ 3,700 − S/ 150). El préstamo sigue en S/ 3,700. La TCEA se eleva porque el cliente paga cuotas calculadas sobre S/ 3,700 pero recibió solo S/ 3,550.

---

### 4.6 TEA personalizada

**Toggle:** "Usar TEA personalizada"
**Campo:** Porcentaje (ej: 72 = 72% anual)

Esta funcionalidad permite probar cualquier tasa de interés **sin modificar la configuración guardada**. Es útil para:

- Encontrar el equilibrio comercial (¿a qué tasa la cuota es accesible para el cliente?)
- Comparar escenarios antes de actualizar la configuración oficial
- Simular propuestas especiales para clientes específicos

> Al desactivar el toggle, el sistema vuelve a usar la TEA configurada para el plazo seleccionado.

---

### 4.7 Seguro de desgravamen

**Toggle:** "Incluir seguro de desgravamen"

El seguro de desgravamen es el seguro que cubre el saldo pendiente del crédito en caso de fallecimiento o invalidez permanente del titular. Está **regulado por la Ley 26702** del Sistema Financiero Peruano.

- Se calcula **sobre el saldo vigente** de cada período, por lo que va disminuyendo a medida que el cliente amortiza.
- La tasa mensual se configura en la pantalla de Configuración Crediticia.
- Para créditos semanales, la tasa se convierte proporcionalmente a 7 días.
- Aparece como una línea separada en cada cuota del cronograma.

---

## 5. Resultados de la simulación

### 5.1 Tarjetas KPI

Las cuatro tarjetas superiores muestran los indicadores más importantes de un vistazo:

| Tarjeta | Color | Qué indica |
|---|---|---|
| **Cuota semanal / mensual** | Azul índigo | Monto de cada cuota (promedio si incluye seguro) |
| **TEA** | Verde azulado | Tasa Efectiva Anual aplicada al crédito |
| **TCEA** | Rojo | Costo total real del crédito (incluye seguro y comisiones) |
| **Total a pagar** | Verde | Suma de todos los desembolsos del cliente |

> La TCEA siempre será igual o mayor a la TEA. Si ambas son iguales, significa que no hay costos adicionales más allá del interés puro.

---

### 5.2 Detalle del financiamiento

Esta sección desglosa todos los componentes del crédito de forma transparente:

#### Sección Capital

| Línea | Descripción |
|---|---|
| Precio del vehículo | Monto base ingresado |
| SOAT | Costo del SOAT (solo aparece si es > 0) |
| Gastos administrativos | Cargo fijo de trámites |
| **Capital base** | Suma de los tres anteriores |
| Comisión de desembolso | Monto calculado + badge "financiada" o "no financiada" |

#### Sección Inicial y Financiamiento

| Línea | Descripción |
|---|---|
| Inicial mínima requerida | Calculada automáticamente sobre el capital base |
| Inicial aplicada | La que realmente se usa (cliente o mínima) |
| **Monto a financiar** | Lo que el sistema presta (en azul destacado) |
| Efectivo neto recibido | Solo aparece si la comisión no es financiada; es lo que el cliente realmente recibe |

#### Sección Tasas y Cuotas

| Línea | Descripción |
|---|---|
| TEM / TES | Tasa periódica derivada de la TEA (mensual o semanal) |
| Cuota base (sin seguro) | Cuota pura de capital + interés |
| Cuota promedio total | Promedio incluyendo el seguro (varía por período) |

#### Sección Totales

| Línea | Descripción |
|---|---|
| Total intereses | Suma de todos los intereses pagados a lo largo del crédito |
| Total seguro de desgravamen | Suma de todos los seguros |
| Comisión al desembolso | Solo si no es financiada |
| **TOTAL A PAGAR** | Todo lo que el cliente desembolsa (inicial + cuotas + comisión si aplica) |

---

### 5.3 Cronograma de pagos

El cronograma es el **detalle cuota por cuota** de todo el crédito. Se activa haciendo clic en **"Ver cronograma"**.

Requerido por la **Resolución SBS N° 11356-2008** como parte de la hoja resumen del crédito.

#### Columnas del cronograma

| Columna | Descripción |
|---|---|
| **#** | Número de cuota (1, 2, 3...) |
| **Fecha** | Fecha exacta de vencimiento de esa cuota |
| **Saldo inicial** | Capital pendiente al inicio del período |
| **Interés** | Interés calculado sobre el saldo inicial del período |
| **Amortización** | Porción de capital que se cancela en esa cuota |
| **Seguro** | Prima del seguro de desgravamen del período |
| **Cuota total** | Suma de Interés + Amortización + Seguro |
| **Saldo final** | Capital pendiente al cierre del período |

#### Fila de totales (pie de tabla)

| Columna | Total |
|---|---|
| Interés | Total de intereses pagados en todo el crédito |
| Amortización | Igual al monto financiado (recuperación completa del capital) |
| Seguro | Total de primas de seguro |
| Cuota total | Total de cuotas (sin contar inicial ni comisión no financiada) |

> **Nota sobre la última cuota:** La última cuota puede diferir ligeramente de las demás porque cancela exactamente el saldo pendiente, absorbiendo los centavos de redondeo acumulados a lo largo del crédito.

---

## 6. Pantalla: Configuración Crediticia

Accesible desde **"Configurar parámetros"** en el simulador, o desde el menú `Calculadora → Configuración`.

Los cambios se guardan en Firestore y aplican **inmediatamente** a todas las simulaciones nuevas. Las simulaciones ya realizadas no se ven afectadas.

> Solo personal con permisos de administrador debería acceder a esta pantalla.

---

### 6.1 Gastos y límites de financiamiento

| Campo | Descripción | Valor típico |
|---|---|---|
| **Gastos administrativos (S/)** | Cargo fijo de trámites, siempre financiable. Se suma al precio del vehículo para formar el capital base | S/ 890 |
| **Inicial mínima (%)** | Porcentaje mínimo que el cliente debe dar como inicial, calculado sobre el capital base | 20% |
| **Monto mínimo a financiar (S/)** | Si el monto resultante es menor a este valor, el sistema lo rechaza | S/ 500 |
| **Monto máximo a financiar (S/)** | Si el monto supera este tope, la inicial se ajusta automáticamente hacia arriba | S/ 5,400 |

> **Ejemplo del tope máximo:**
> Capital base: S/ 5,800 · Inicial mínima (20%): S/ 1,160 → Financiamiento: S/ 4,640
> Si el tope máximo es S/ 4,200, la inicial se ajusta a S/ 1,600 para que el financiamiento sea exactamente S/ 4,200.

---

### 6.2 Tasas de interés y seguro

| Campo | Descripción | Valor típico |
|---|---|---|
| **TEA por defecto (%)** | Tasa anual que se aplica cuando el plazo ingresado no tiene TEA específica configurada | 72% |
| **Seguro de desgravamen mensual (%)** | Tasa mensual sobre el saldo vigente. Regulada por Ley 26702 SBS | 0.04% |

> Las tasas se ingresan en porcentaje (ej: 72 equivale a 72% = 0.72 en ratio).

---

### 6.3 Comisión por defecto

Define la comisión que se aplica cuando el simulador no especifica una explícita.

| Campo | Descripción |
|---|---|
| **Tipo** | `Monto fijo (S/)` o `Porcentaje del capital base (%)` |
| **Valor** | Monto en soles o porcentaje según el tipo |
| **Financiar comisión** | Activo = se suma al préstamo; Inactivo = se cobra al desembolso |

> Ingresar 0 en el campo Valor equivale a "sin comisión".

---

### 6.4 Plazos disponibles

Los plazos configurados aquí aparecen como **chips de selección rápida** en el simulador, filtrados por frecuencia.

Por cada plazo se configura:

| Campo | Descripción | Ejemplo |
|---|---|---|
| **Frecuencia** | `Mensual` o `Semanal` | Mensual |
| **Cuotas** | Número de cuotas (meses o semanas) | 10 |
| **TEA (%)** | Tasa Efectiva Anual específica para este plazo | 72.63% |
| **Etiqueta** | Texto descriptivo que aparece bajo el número en el chip | "Recomendado" |

**Botones de agregar:** Hay dos botones separados — uno para plazos mensuales y otro para semanales — que pre-rellenan los valores por defecto más adecuados para cada frecuencia.

**Recomendación de configuración:**

```
Plazos mensuales sugeridos:
  8 meses  → TEA 65.26% → "Pago rápido"
  10 meses → TEA 72.63% → "Recomendado"
  12 meses → TEA 79.19% → "Cuota menor"

Plazos semanales sugeridos (equivalentes aproximados):
  32 semanas → TEA 65.26% → "Pago rápido"
  40 semanas → TEA 72.63% → "Recomendado"
  48 semanas → TEA 79.19% → "Cuota menor"
```

> Siempre definir al menos un plazo por frecuencia que se vaya a ofrecer a los clientes.

---

## 7. Glosario financiero

### TEA — Tasa Efectiva Anual
La tasa de interés pura del crédito, expresada anualmente. Es el costo del dinero prestado sin considerar seguros ni comisiones. El sistema la convierte internamente a tasa periódica usando capitalización compuesta:

- **Mensual:** `tasa = (1 + TEA)^(1/12) − 1`
- **Semanal:** `tasa = (1 + TEA)^(7/360) − 1`

> No se usa división simple (TEA/12 o TEA/52) porque eso subestimaría el costo real del crédito.

### TCEA — Tasa de Costo Efectivo Anual
Indicador de transparencia **exigido por la SBS**. Representa el costo total real del crédito para el cliente, incluyendo intereses, seguro de desgravamen y comisiones. Se calcula mediante el método de la TIR (Tasa Interna de Retorno) usando Newton-Raphson:

```
TCEA responde a la pregunta:
"¿A qué tasa anual equivale todo lo que el cliente pagará respecto a lo que efectivamente recibió?"
```

La TCEA siempre será ≥ TEA. La diferencia entre ambas aumenta cuando:
1. Se incluye seguro de desgravamen
2. Hay comisiones no financiadas

### Sistema Francés de amortización
Sistema de cuota fija: el cliente paga siempre el mismo monto base. Al inicio, la mayor parte de la cuota es interés y poco capital. Con el tiempo, la proporción se invierte: cada vez más capital y menos interés. Esto se conoce como **amortización creciente**.

### TEM — Tasa Efectiva Mensual
La TEA convertida a tasa mensual. Es la tasa que se aplica período a período para calcular el interés de cada cuota en créditos mensuales.

### TES — Tasa Efectiva Semanal
La TEA convertida a tasa semanal. Equivalente al TEM pero para créditos de pago semanal.

### Seguro de Desgravamen
Seguro que cubre el saldo pendiente del crédito ante el fallecimiento o invalidez permanente del titular. Regulado por la **Ley 26702** (Ley General del Sistema Financiero y del Sistema de Seguros). Se calcula sobre el saldo vigente de cada período, por lo que disminuye a medida que el cliente amortiza capital.

### Capital base
Suma de todos los costos financiables fijos:
```
Capital base = Precio del vehículo + SOAT + Gastos administrativos
```

### Efectivo neto
Lo que el cliente realmente recibe cuando hay una comisión **no financiada**:
```
Efectivo neto = Monto a financiar − Comisión no financiada
```
Este valor es el que determina la TCEA real.

---

## 8. Preguntas frecuentes

**¿Por qué la TCEA es diferente a la TEA?**
La TCEA incluye todos los costos reales: seguro de desgravamen y comisiones. La TEA es solo el interés puro. Si ambas son idénticas, significa que no hay seguros ni comisiones adicionales.

**¿La comisión siempre eleva la TCEA?**
Sí, pero de distinta forma:
- **Financiada:** La eleva levemente (el capital sube pero las cuotas también)
- **No financiada:** La eleva significativamente (el cliente paga cuotas sobre el monto completo pero recibió menos)

**¿El cronograma puede tener la última cuota diferente?**
Sí, es normal. Los redondeos acumulados a lo largo del crédito se absorben en la última cuota. La diferencia suele ser de centavos y el saldo final queda en S/ 0.00 exactos.

**¿Qué pasa si el cliente quiere dar más inicial de la mínima?**
El simulador respeta cualquier inicial mayor a la mínima. Esto reduce el monto financiado y, por tanto, cada cuota.

**¿El cálculo del seguro es proporcional para pagos semanales?**
Sí. La tasa mensual configurada se convierte proporcionalmente: `tasa_semanal = tasa_mensual × (7/30)`.

**¿Por qué las tasas usan capitalización compuesta y no división simple?**
La regulación financiera y la matemática del Sistema Francés exigen capitalización compuesta. Dividir la TEA entre 12 o entre 52 subestimaría el interés y produciría una TCEA incorrecta.

**¿Los cambios en Configuración afectan créditos activos?**
No. Los parámetros se aplican solo a simulaciones y créditos nuevos. Los créditos ya originados mantienen sus condiciones originales.

**¿Puedo simular sin usar los plazos preconfigurados?**
Sí. El campo de número de cuotas acepta cualquier valor entre 1 y 260. Si el plazo no coincide con ninguno preconfigurado, se aplica la TEA por defecto.

---

*Manual elaborado para uso interno del equipo Motoya. Los cálculos cumplen con la normativa SBS Perú vigente a la fecha de emisión.*
