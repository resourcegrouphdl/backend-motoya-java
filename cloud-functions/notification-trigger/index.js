/**
 * Cloud Function: Firestore trigger → despierta Cloud Run para procesar notificaciones.
 *
 * Trigger: onCreate en collection "notification_events"
 * Deploy:
 *   gcloud functions deploy notificationTrigger \
 *     --runtime nodejs20 \
 *     --trigger-event providers/cloud.firestore/eventTypes/document.create \
 *     --trigger-resource "projects/motoya-form/databases/(default)/documents/notification_events/{eventId}" \
 *     --set-env-vars CLOUD_RUN_URL=https://tu-servicio-xxx.run.app,INTERNAL_TOKEN=tu-token \
 *     --region us-central1 \
 *     --memory 128MB \
 *     --timeout 30s
 */

const https = require('https');
const { URL } = require('url');

const CLOUD_RUN_URL  = process.env.CLOUD_RUN_URL;   // ej: https://backendmotoya-xxx.run.app
const INTERNAL_TOKEN = process.env.INTERNAL_TOKEN;

exports.notificationTrigger = async (event, context) => {
  console.log(`[TRIGGER] Nuevo evento en notification_events: ${context.params.eventId}`);

  // Pequeño delay para que Firestore confirme la escritura antes de que Cloud Run lea
  await new Promise(r => setTimeout(r, 500));

  try {
    await callBackend(`${CLOUD_RUN_URL}/internal/notifications/process`);
    console.log('[TRIGGER] Backend procesó el evento correctamente');
  } catch (err) {
    console.error('[TRIGGER] Error llamando al backend:', err.message);
    // No lanzar error: Firestore no reintentará el trigger
  }
};

function callBackend(url) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Internal-Token': INTERNAL_TOKEN,
      },
      timeout: 25000,
    };

    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        console.log(`[TRIGGER] Respuesta Cloud Run: ${res.statusCode} ${body}`);
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body);
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${body}`));
        }
      });
    });

    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('Timeout llamando a Cloud Run')));
    req.end();
  });
}
