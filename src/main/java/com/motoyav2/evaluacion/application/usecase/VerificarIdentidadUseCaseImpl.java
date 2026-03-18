package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.VerificarIdentidadCommand;
import com.motoyav2.evaluacion.application.dto.VerificacionIdentidadResult;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.port.in.VerificarIdentidadUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.external.FactilizaApiClient;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifica la identidad del cliente consultando la API de Factiliza.
 *
 * Estructura real de respuestas Factiliza:
 *   DNI/CEE → { status, data: { nombres, apellido_paterno, apellido_materno,
 *                                direccion, ubigeo, departamento, provincia, distrito } }
 *   Licencia → { status, data: { numero_documento, nombre_completo,
 *                                licencia: { numero, categoria, fecha_expedicion,
 *                                            fecha_vencimiento, estado, restricciones } } }
 */
@Service
@RequiredArgsConstructor
public class VerificarIdentidadUseCaseImpl implements VerificarIdentidadUseCase {

    private final ClienteRepository clienteRepository;
    private final FactilizaApiClient factilizaApiClient;

    @Override
    public Mono<VerificacionIdentidadResult> ejecutar(VerificarIdentidadCommand cmd) {
        return clienteRepository.findById(cmd.clienteId())
                .switchIfEmpty(Mono.error(
                        new RecursoNoEncontradoException("Cliente no encontrado: " + cmd.clienteId())))
                .flatMap(cliente -> verificar(cliente, cmd));
    }

    // ── Orquestación ──────────────────────────────────────────────────────
    private Mono<VerificacionIdentidadResult> verificar(Cliente cliente, VerificarIdentidadCommand cmd) {
        String docType   = cliente.getDocumentType();
        String docNumber = cliente.getDocumentNumber();

        if (docNumber == null || docNumber.isBlank()) {
            return persistResult(cmd.clienteId(), VerificacionIdentidadResult.builder()
                    .documentType(docType)
                    .documentNumber(docNumber)
                    .exitoso(false)
                    .observaciones("Número de documento vacío")
                    .verificadoPor(cmd.usuarioId())
                    .build());
        }

        boolean esDni = "DNI".equalsIgnoreCase(docType);
        boolean esCee = "CE".equalsIgnoreCase(docType) || "CARNET_EXTRANJERIA".equalsIgnoreCase(docType);

        Mono<Map<String, Object>> docQuery = esDni
                ? factilizaApiClient.consultarDni(docNumber)
                : esCee ? factilizaApiClient.consultarCee(docNumber) : Mono.empty();

        // Licencia solo aplica a titulares con DNI que declaran tener licencia
        boolean tieneConducir = tieneLicencia(cliente);
        Mono<Map<String, Object>> licQuery = (esDni && tieneConducir)
                ? factilizaApiClient.consultarLicencia(docNumber)
                : Mono.empty();

        return Mono.zip(
                docQuery.defaultIfEmpty(Map.of()),
                licQuery.defaultIfEmpty(Map.of())
        ).flatMap(tuple ->
                persistResult(cmd.clienteId(),
                        buildResult(cliente, tuple.getT1(), tuple.getT2(), cmd.usuarioId())));
    }

    // ── Construcción del resultado ────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private VerificacionIdentidadResult buildResult(
            Cliente cliente,
            Map<String, Object> docResponse,   // respuesta cruda DNI/CEE
            Map<String, Object> licResponse,   // respuesta cruda Licencia
            String verificadoPor) {

        // Factiliza envuelve los datos en "data" — extraemos ese nivel
        Map<String, Object> docData = extractData(docResponse);
        Map<String, Object> licData = extractLicencia(licResponse); // navega data.licencia

        boolean apiOk = !docData.isEmpty();

        // ── Campos de identidad (DNI / CEE) ───────────────────────────────
        String apiNombres    = str(docData, "nombres");
        String apiApePaterno = str(docData, "apellido_paterno");
        String apiApeMaterno = str(docData, "apellido_materno");
        String apiDireccion  = str(docData, "direccion");
        String apiUbigeo     = str(docData, "ubigeo");
        String apiDpto       = str(docData, "departamento");
        String apiProv       = str(docData, "provincia");
        String apiDist       = str(docData, "distrito");

        // ── Campos demográficos (presentes en DNI, ausentes en CEE) ──────
        String apiSexo           = blankToNull(str(docData, "sexo"));
        String apiFechaNacimiento = blankToNull(str(docData, "fecha_nacimiento"));

        // ── Campos de licencia (data.licencia) ────────────────────────────
        String licNumero      = str(licData, "numero");
        String licCategoria   = str(licData, "categoria");
        String licEstado      = str(licData, "estado");
        String licVencimiento = str(licData, "fecha_vencimiento");
        String licRestricciones = str(licData, "restricciones");

        boolean licVigente = "VIGENTE".equalsIgnoreCase(licEstado);
        boolean tieneConducir = tieneLicencia(cliente);

        // "SIN RESTRICCIONES" o vacío = sin restricción real
        boolean tieneRestriccion = licRestricciones != null
                && !licRestricciones.isBlank()
                && !licRestricciones.equalsIgnoreCase("SIN RESTRICCIONES");

        // ── Comparaciones de identidad ────────────────────────────────────
        Boolean coincideNombres = apiOk
                ? normalize(apiNombres).equals(normalize(cliente.getNombres()))
                : null;

        Boolean coincideApellidos = apiOk
                ? normalize(apiApePaterno).equals(normalize(cliente.getApellidoPaterno()))
                && normalize(apiApeMaterno).equals(normalize(cliente.getApellidoMaterno()))
                : null;

        // ── Comparaciones demográficas (solo cuando la API trae el dato) ──
        String clienteSexo           = blankToNull(cliente.getSexo());
        String clienteFechaNacimiento = blankToNull(cliente.getFechaNacimiento());

        Boolean coincideSexo = (apiSexo != null && clienteSexo != null)
                ? normalize(apiSexo).equals(normalize(clienteSexo))
                : null;   // null = sin dato suficiente para comparar

        Boolean coincideFechaNacimiento = (apiFechaNacimiento != null && clienteFechaNacimiento != null)
                ? normalize(apiFechaNacimiento).equals(normalize(clienteFechaNacimiento))
                : null;

        // ── Auto-relleno: campo vacío en BD y API trae valor ─────────────
        boolean autorellenoSexo           = apiSexo != null && clienteSexo == null;
        boolean autorellenoFechaNacimiento = apiFechaNacimiento != null && clienteFechaNacimiento == null;

        // ── Observaciones relevantes para evaluación ──────────────────────
        // La dirección NO se compara (puede ser antigua por mudanza/alquiler)
        List<String> obs = new ArrayList<>();
        if (!apiOk)
            obs.add("No se obtuvo respuesta de la API para el documento");
        if (Boolean.FALSE.equals(coincideNombres))
            obs.add("Nombres no coinciden con el documento");
        if (Boolean.FALSE.equals(coincideApellidos))
            obs.add("Apellidos no coinciden con el documento");
        if (tieneConducir && !licData.isEmpty() && !licVigente)
            obs.add("Licencia de conducir VENCIDA o no encontrada");
        if (tieneConducir && tieneRestriccion)
            obs.add("Licencia con restricción: " + licRestricciones);

        return VerificacionIdentidadResult.builder()
                .documentType(cliente.getDocumentType())
                .documentNumber(cliente.getDocumentNumber())
                .apiNombres(apiNombres)
                .apiApellidoPaterno(apiApePaterno)
                .apiApellidoMaterno(apiApeMaterno)
                .apiDireccion(apiDireccion)
                .apiUbigeo(apiUbigeo)
                .apiDepartamento(apiDpto)
                .apiProvincia(apiProv)
                .apiDistrito(apiDist)
                .apiSexo(apiSexo)
                .apiFechaNacimiento(apiFechaNacimiento)
                .licenciaNumero(licNumero)
                .licenciaCategoria(licCategoria)
                .licenciaEstado(licEstado)
                .licenciaVencimiento(licVencimiento)
                .licenciaRestricciones(licRestricciones)
                .licenciaTieneRestricion(tieneConducir && !licData.isEmpty() ? tieneRestriccion : null)
                .coincideNombres(coincideNombres)
                .coincideApellidos(coincideApellidos)
                .coincideSexo(coincideSexo)
                .coincideFechaNacimiento(coincideFechaNacimiento)
                .licenciaVigente(tieneConducir && !licData.isEmpty() ? licVigente : null)
                .tieneConducir(tieneConducir)
                .autorellenoSexo(autorellenoSexo)
                .autorellenoFechaNacimiento(autorellenoFechaNacimiento)
                .verificadoPor(verificadoPor)
                .observaciones(obs.isEmpty() ? null : String.join("; ", obs))
                .exitoso(apiOk)
                .build();
    }

    // ── Persistencia ─────────────────────────────────────────────────────
    private Mono<VerificacionIdentidadResult> persistResult(
            String clienteId, VerificacionIdentidadResult r) {

        Map<String, Object> verMap = new HashMap<>();
        verMap.put("documentType",            r.getDocumentType());
        verMap.put("documentNumber",          r.getDocumentNumber());
        verMap.put("apiNombres",              r.getApiNombres());
        verMap.put("apiApellidoPaterno",      r.getApiApellidoPaterno());
        verMap.put("apiApellidoMaterno",      r.getApiApellidoMaterno());
        verMap.put("apiDireccion",            r.getApiDireccion());
        verMap.put("apiUbigeo",               r.getApiUbigeo());
        verMap.put("apiDepartamento",         r.getApiDepartamento());
        verMap.put("apiProvincia",            r.getApiProvincia());
        verMap.put("apiDistrito",             r.getApiDistrito());
        verMap.put("licenciaNumero",          r.getLicenciaNumero());
        verMap.put("licenciaCategoria",       r.getLicenciaCategoria());
        verMap.put("licenciaEstado",          r.getLicenciaEstado());
        verMap.put("licenciaVencimiento",     r.getLicenciaVencimiento());
        verMap.put("licenciaRestricciones",   r.getLicenciaRestricciones());
        verMap.put("licenciaTieneRestricion", r.getLicenciaTieneRestricion());
        verMap.put("coincideNombres",         r.getCoincideNombres());
        verMap.put("coincideApellidos",       r.getCoincideApellidos());
        verMap.put("apiSexo",                 r.getApiSexo());
        verMap.put("apiFechaNacimiento",      r.getApiFechaNacimiento());
        verMap.put("coincideSexo",            r.getCoincideSexo());
        verMap.put("coincideFechaNacimiento", r.getCoincideFechaNacimiento());
        verMap.put("autorellenoSexo",         r.getAutorellenoSexo());
        verMap.put("autorellenoFechaNacimiento", r.getAutorellenoFechaNacimiento());
        verMap.put("licenciaVigente",         r.getLicenciaVigente());
        verMap.put("tieneConducir",           r.getTieneConducir());
        verMap.put("observaciones",           r.getObservaciones());
        verMap.put("exitoso",                 r.isExitoso());
        verMap.put("verificadoPor",           r.getVerificadoPor());
        verMap.put("fechaVerificacion",       Timestamp.now());

        Map<String, Object> updates = new HashMap<>();
        updates.put("verificacionIdentidad", verMap);
        updates.put("updatedAt", Timestamp.now());

        // Auto-relleno: escribir campos demográficos al nivel raíz del cliente
        // solo cuando estaban vacíos y la API los trajo
        if (Boolean.TRUE.equals(r.getAutorellenoSexo())) {
            updates.put("sexo", r.getApiSexo());
        }
        if (Boolean.TRUE.equals(r.getAutorellenoFechaNacimiento())) {
            updates.put("fechaNacimiento", r.getApiFechaNacimiento());
        }

        return clienteRepository.updateFields(clienteId, updates).thenReturn(r);
    }

    // ── Helpers de extracción de la respuesta Factiliza ───────────────────

    /**
     * Factiliza envuelve todos los datos en "data".
     * { status, message, data: { ... } } → devuelve el mapa interno.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractData(Map<String, Object> response) {
        if (response == null || response.isEmpty()) return Map.of();
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    /**
     * La licencia viene anidada: { data: { licencia: { numero, estado, ... } } }
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractLicencia(Map<String, Object> response) {
        Map<String, Object> data = extractData(response);
        if (data.isEmpty()) return Map.of();
        Object licencia = data.get("licencia");
        if (licencia instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private static boolean tieneLicencia(Cliente c) {
        return "si".equalsIgnoreCase(c.getLicenciaConducir())
                || Boolean.parseBoolean(c.getLicenciaConducir());
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    /** Converts a blank string (null, empty, or whitespace-only) to null. */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase()
                .replace("Á","A").replace("É","E").replace("Í","I")
                .replace("Ó","O").replace("Ú","U").replace("Ü","U")
                .replace("Ñ","N");
    }
}
