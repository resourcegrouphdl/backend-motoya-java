/**
 * Migración Excel → riesgo_registros (Firestore)
 *
 * USO:
 *   1. npm install
 *   2. Exportar el Excel como CSV UTF-8 y nombrarlo datos.csv en esta carpeta
 *   3. Ajustar el bloque CONFIG debajo (nombres de columnas, separador, etc.)
 *   4. npm run dry-run   → simula sin escribir nada
 *   5. npm run migrar    → escribe en Firestore
 */

'use strict';

const fs    = require('fs');
const path  = require('path');
const { parse } = require('csv-parse/sync');
const admin = require('firebase-admin');

// ═══════════════════════════════════════════════════════════════════════════════
//  CONFIG — AJUSTAR AQUÍ ANTES DE CORRER
// ═══════════════════════════════════════════════════════════════════════════════

const CONFIG = {

  // ── Rutas ──────────────────────────────────────────────────────────────────
  csvPath:            path.resolve(__dirname, 'datos.csv'),
  serviceAccountPath: path.resolve(__dirname, '../../src/main/resources/motoya-form-firebase-adminsdk-m3iw6-2a1216555b.json'),

  // ── Separador del CSV ───────────────────────────────────────────────────────
  // Excel peruano suele exportar con ';'. Si los campos están juntos, cambiar a ','
  delimiter: ';',

  // ── Mapeo de columnas del CSV ───────────────────────────────────────────────
  // Poner el nombre EXACTO de cada cabecera (sensible a mayúsculas/espacios).
  // Dejar '' si la columna no existe en tu Excel.
  columnas: {
    nombre:           'NOMBRE',                // nombre completo del sujeto registrado
    dni:              'DNI',                   // DNI del titular (puede estar vacío)
    telefonoFiador:   'NRO CELULAR DEL FIADOR',
    nombreRef1:       '',                       // si hay nombre de ref 1, ej: 'NOMBRE REF 1'
    telefonoRef1:     'REFERENCIA 1 CELULAR',
    nombreRef2:       '',
    telefonoRef2:     'REFERENCIA 2 CELULAR',
    nombreRef3:       '',
    telefonoRef3:     'REFERENCIA 3 CELULAR',
    pagoPuntual:      'PAGO PUNTUAL',
    pagoPorAtraso:    'PAGO POR ATRASO',
    pagoConDescuento: 'PAGO CON DESCUENTO',
    // Columna opcional de descripción libre. Dejar '' si no existe.
    descripcion:      '',
  },

  // ── Clasificación automática ────────────────────────────────────────────────
  // Ajustar si la lógica de negocio es diferente.
  clasificacion: {
    pagoConDescuento: { tipoRiesgo: 'DEUDA_NEGOCIADA',    estadoRegistro: 'NEGOCIADO',        nivelRiesgo: 'AMARILLO' },
    pagoPorAtraso:    { tipoRiesgo: 'INCUMPLIMIENTO_PAGO', estadoRegistro: 'ACTIVO',           nivelRiesgo: 'ROJO'     },
    pagoPuntual:      { tipoRiesgo: 'INCUMPLIMIENTO_PAGO', estadoRegistro: 'BAJO_VIGILANCIA',  nivelRiesgo: 'AMARILLO' },
    sinMarca:         { tipoRiesgo: 'OTRO',                estadoRegistro: 'ACTIVO',           nivelRiesgo: 'ROJO'     },
  },

  // UID que aparecerá como registradoPor en cada documento
  registradoPor: 'migracion-excel-2026',

};

// ═══════════════════════════════════════════════════════════════════════════════
//  SCRIPT — no modificar debajo de esta línea salvo que sepas lo que haces
// ═══════════════════════════════════════════════════════════════════════════════

const DRY_RUN = process.argv.includes('--dry-run');
const COL = 'riesgo_registros';

// ── Inicializar Firebase ────────────────────────────────────────────────────

const serviceAccount = JSON.parse(fs.readFileSync(CONFIG.serviceAccountPath, 'utf8'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId:  serviceAccount.project_id,
});

const db = admin.firestore();

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Lee una columna del CSV de forma segura. */
function leer(fila, columna) {
  if (!columna) return '';
  const val = fila[columna];
  return val != null ? String(val).trim() : '';
}

/** Decide si un campo marca "sí": SI, S, X, 1, YES, ✓, TRUE */
function esMarcado(val) {
  return ['si', 'sí', 's', 'x', '1', 'yes', 'true', '✓', 'v'].includes(
    String(val || '').trim().toLowerCase()
  );
}

/** Normaliza un teléfono peruano a 9 dígitos. */
function normalizarTel(tel) {
  if (!tel) return '';
  let d = String(tel).replace(/[^0-9]/g, '');
  if (d.startsWith('51') && d.length === 11) d = d.slice(2);
  return d.length >= 9 ? d : '';
}

/** Agrega un teléfono al array si es válido y no está duplicado. */
function agregarTel(arr, raw) {
  const n = normalizarTel(raw);
  if (n && !arr.includes(n)) arr.push(n);
}

/** Infiere la clasificación (tipoRiesgo, estado, nivel) a partir de columnas de pago. */
function inferirClasificacion(fila) {
  if (esMarcado(leer(fila, CONFIG.columnas.pagoConDescuento))) return CONFIG.clasificacion.pagoConDescuento;
  if (esMarcado(leer(fila, CONFIG.columnas.pagoPorAtraso)))    return CONFIG.clasificacion.pagoPorAtraso;
  if (esMarcado(leer(fila, CONFIG.columnas.pagoPuntual)))      return CONFIG.clasificacion.pagoPuntual;
  return CONFIG.clasificacion.sinMarca;
}

/** Construye el objeto Firestore a partir de una fila CSV. */
function construirDocumento(fila, index) {
  const cols = CONFIG.columnas;

  const nombre = leer(fila, cols.nombre) || `SUJETO-${index + 1}`;
  const dni    = leer(fila, cols.dni) || null;

  const telefonos = [];
  agregarTel(telefonos, leer(fila, cols.telefonoFiador));
  agregarTel(telefonos, leer(fila, cols.telefonoRef1));
  agregarTel(telefonos, leer(fila, cols.telefonoRef2));
  agregarTel(telefonos, leer(fila, cols.telefonoRef3));

  if (!nombre && telefonos.length === 0 && !dni) return null; // fila vacía

  const clases = inferirClasificacion(fila);

  // Descripción automática
  const pagos = [];
  if (esMarcado(leer(fila, cols.pagoPuntual)))      pagos.push('pago puntual');
  if (esMarcado(leer(fila, cols.pagoPorAtraso)))    pagos.push('pago por atraso');
  if (esMarcado(leer(fila, cols.pagoConDescuento))) pagos.push('pago con descuento');

  const descBase  = pagos.length > 0
    ? `Historial de pagos: ${pagos.join(', ')}.`
    : 'Sin historial de pagos registrado.';
  const descExtra = leer(fila, cols.descripcion);
  const descripcion = [descBase, 'Migrado desde Excel de riesgo interno.', descExtra]
    .filter(Boolean).join(' ');

  const ahora = admin.firestore.Timestamp.now();

  return {
    nombreRegistrado:       nombre,
    dniRegistrado:          dni,
    telefonos,
    tipoSujeto:             'FIADOR',
    nivelRiesgo:            clases.nivelRiesgo,
    estadoRegistro:         clases.estadoRegistro,
    tipoRiesgo:             clases.tipoRiesgo,
    contratoIdRelacionado:  null,
    solicitudIdRelacionado: null,
    montoDeudaPendiente:    null,
    fechaIncidente:         ahora,
    descripcion,
    evidencias:              [],
    condicionesRehabilitacion: [],
    registradoPor:           CONFIG.registradoPor,
    historialCambios:        [],
    fechaRegistro:           ahora,
    updatedAt:               ahora,
  };
}

// ── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  console.log(`\n${'═'.repeat(60)}`);
  console.log(` MIGRACIÓN LISTA NEGRA → riesgo_registros`);
  console.log(` Modo: ${DRY_RUN ? '🔍 DRY RUN (sin escritura)' : '🚀 LIVE (escribe en Firestore)'}`);
  console.log(`${'═'.repeat(60)}\n`);

  // Leer CSV
  if (!fs.existsSync(CONFIG.csvPath)) {
    console.error(`❌ No se encontró el archivo CSV: ${CONFIG.csvPath}`);
    console.error('   Exportá el Excel como CSV UTF-8 y nombralo datos.csv en esta carpeta.');
    process.exit(1);
  }

  const contenido = fs.readFileSync(CONFIG.csvPath, 'utf8');
  let filas;
  try {
    filas = parse(contenido, {
      columns:          true,       // primera fila = cabeceras
      skip_empty_lines: true,
      delimiter:        CONFIG.delimiter,
      trim:             true,
      bom:              true,       // strip BOM de Excel
    });
  } catch (e) {
    console.error('❌ Error al parsear el CSV:', e.message);
    console.error('   Verificá que el separador en CONFIG.delimiter sea correcto (coma o punto y coma).');
    process.exit(1);
  }

  console.log(`📄 Filas encontradas en CSV: ${filas.length}`);

  // Validar cabeceras
  const cabeceras = Object.keys(filas[0] || {});
  console.log(`📋 Columnas detectadas: ${cabeceras.join(' | ')}\n`);

  const columnasFaltantes = Object.entries(CONFIG.columnas)
    .filter(([, col]) => col && !cabeceras.includes(col))
    .map(([key, col]) => `  ${key}: "${col}"`);

  if (columnasFaltantes.length > 0) {
    console.warn('⚠️  Las siguientes columnas del CONFIG no se encontraron en el CSV:');
    columnasFaltantes.forEach(c => console.warn(c));
    console.warn('   Ajustá CONFIG.columnas con los nombres exactos del CSV.\n');
  }

  // Construir documentos
  const docs = [];
  const omitidas = [];

  filas.forEach((fila, i) => {
    const doc = construirDocumento(fila, i);
    if (!doc) {
      omitidas.push(i + 2); // +2 porque fila 1 es cabecera y arrays son 0-indexed
    } else {
      docs.push(doc);
    }
  });

  if (omitidas.length > 0) {
    console.log(`⏭️  Filas omitidas (vacías): ${omitidas.join(', ')}`);
  }

  console.log(`✅ Documentos a migrar: ${docs.length}\n`);

  // Mostrar preview
  docs.forEach((doc, i) => {
    const estado = `[${doc.nivelRiesgo}/${doc.estadoRegistro}]`;
    const tels   = doc.telefonos.join(', ') || '—';
    console.log(`  ${String(i + 1).padStart(2, '0')}. ${doc.nombreRegistrado} ${doc.dniRegistrado ? `(DNI: ${doc.dniRegistrado})` : ''}`);
    console.log(`      ${estado} ${doc.tipoRiesgo} | tels: ${tels}`);
    console.log(`      ${doc.descripcion.substring(0, 80)}…`);
  });

  if (DRY_RUN) {
    console.log('\n🔍 DRY RUN completado. Ningún dato fue escrito.');
    console.log('   Corré sin --dry-run para escribir en Firestore: npm run migrar\n');
    process.exit(0);
  }

  // Verificar duplicados en Firestore
  console.log('\n🔎 Verificando duplicados en Firestore…');
  const dnisExistentes = new Set();
  const telsExistentes = new Set();

  const snapDnis = await db.collection(COL).where('dniRegistrado', '!=', null).get();
  snapDnis.forEach(d => { if (d.data().dniRegistrado) dnisExistentes.add(d.data().dniRegistrado); });

  const snapTels = await db.collection(COL).get();
  snapTels.forEach(d => {
    (d.data().telefonos || []).forEach(t => telsExistentes.add(t));
  });

  // Filtrar duplicados
  const nuevos = [];
  const saltados = [];

  docs.forEach(doc => {
    const dniDup = doc.dniRegistrado && dnisExistentes.has(doc.dniRegistrado);
    const telDup = doc.telefonos.some(t => telsExistentes.has(t));
    if (dniDup || telDup) {
      saltados.push(`${doc.nombreRegistrado} (${dniDup ? 'DNI duplicado' : 'teléfono duplicado'})`);
    } else {
      nuevos.push(doc);
    }
  });

  if (saltados.length > 0) {
    console.log(`⚠️  Saltados por duplicado:\n${saltados.map(s => '  - ' + s).join('\n')}`);
  }

  if (nuevos.length === 0) {
    console.log('\n✅ No hay registros nuevos para migrar (todos ya existen).');
    process.exit(0);
  }

  console.log(`\n💾 Escribiendo ${nuevos.length} documentos en Firestore…`);

  // Escribir en batches de 500
  const BATCH_SIZE = 500;
  let escritos = 0;

  for (let i = 0; i < nuevos.length; i += BATCH_SIZE) {
    const lote = nuevos.slice(i, i + BATCH_SIZE);
    const batch = db.batch();
    lote.forEach(doc => {
      const ref = db.collection(COL).doc(); // auto-ID
      batch.set(ref, doc);
    });
    await batch.commit();
    escritos += lote.length;
    console.log(`  ✓ Lote ${Math.floor(i / BATCH_SIZE) + 1}: ${lote.length} documentos escritos (total: ${escritos})`);
  }

  console.log(`\n${'═'.repeat(60)}`);
  console.log(` ✅ MIGRACIÓN COMPLETADA`);
  console.log(`    Escritos:  ${escritos}`);
  console.log(`    Saltados:  ${saltados.length}`);
  console.log(`    Omitidos:  ${omitidas.length}`);
  console.log(`${'═'.repeat(60)}\n`);
}

main().catch(err => {
  console.error('\n❌ Error fatal:', err.message);
  process.exit(1);
});
