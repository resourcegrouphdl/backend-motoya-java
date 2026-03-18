package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.VerificarIdentidadCommand;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.port.in.VerificarIdentidadUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.application.dto.VerificacionIdentidadResult;
import com.motoyav2.evaluacion.infrastructure.adapter.out.external.FactilizaApiClient;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private Mono<VerificacionIdentidadResult> verificar(Cliente cliente, VerificarIdentidadCommand cmd) {
        String docType   = cliente.getDocumentType();
        String docNumber = cliente.getDocumentNumber();

        if (docNumber == null || docNumber.isBlank()) {
            VerificacionIdentidadResult result = VerificacionIdentidadResult.builder()
                    .documentType(docType)
                    .documentNumber(docNumber)
                    .exitoso(false)
                    .observaciones("Número de documento vacío")
                    .verificadoPor(cmd.usuarioId())
                    .build();
            return persistResult(cmd.clienteId(), result);
        }

        boolean esDni = "DNI".equalsIgnoreCase(docType);
        boolean esCee = "CE".equalsIgnoreCase(docType) || "CARNET_EXTRANJERIA".equalsIgnoreCase(docType);

        Mono<Map<String, Object>> docQuery = esDni
                ? factilizaApiClient.consultarDni(docNumber)
                : esCee ? factilizaApiClient.consultarCee(docNumber) : Mono.empty();

        Mono<Map<String, Object>> licQuery = esDni
                ? factilizaApiClient.consultarLicencia(docNumber)
                : Mono.empty();

        return Mono.zip(
                docQuery.defaultIfEmpty(Map.of()),
                licQuery.defaultIfEmpty(Map.of())
        ).flatMap(tuple -> {
            Map<String, Object> docData = tuple.getT1();
            Map<String, Object> licData = tuple.getT2();

            VerificacionIdentidadResult result = buildResult(cliente, docData, licData, cmd.usuarioId());
            return persistResult(cmd.clienteId(), result);
        });
    }

    // ── Build result comparing API data with stored client data ──────────
    private VerificacionIdentidadResult buildResult(
            Cliente cliente,
            Map<String, Object> docData,
            Map<String, Object> licData,
            String verificadoPor) {

        boolean apiOk = !docData.isEmpty();

        // Extract API fields (Factiliza returns them in the root map)
        String apiNombres   = str(docData, "nombres");
        String apiApePaterno = str(docData, "apellido_paterno");
        String apiApeMaterno = str(docData, "apellido_materno");
        String apiDireccion = str(docData, "direccion");
        String apiUbigeo    = str(docData, "ubigeo");
        String apiDpto      = str(docData, "departamento");
        String apiProv      = str(docData, "provincia");
        String apiDist      = str(docData, "distrito");

        // Licence fields
        String licNumero   = str(licData, "numero");
        String licCategoria = str(licData, "categoria");
        String licEstado   = str(licData, "estado");
        String licVencim   = str(licData, "fecha_hasta");
        boolean licVigente = "VIGENTE".equalsIgnoreCase(licEstado);
        boolean tieneConducir = "si".equalsIgnoreCase(cliente.getLicenciaConducir())
                || Boolean.parseBoolean(cliente.getLicenciaConducir());

        // Compare names (case-insensitive, trimmed)
        Boolean coincideNombres = apiOk
                ? normalize(apiNombres).equals(normalize(cliente.getNombres()))
                : null;

        Boolean coincideApellidos = apiOk
                ? normalize(apiApePaterno).equals(normalize(cliente.getApellidoPaterno()))
                && normalize(apiApeMaterno).equals(normalize(cliente.getApellidoMaterno()))
                : null;

        // Nota: NO se compara dirección — la del documento puede diferir de la
        // residencia actual declarada (cliente puede estar alquilando o haberse mudado).
        // Se almacena como referencia informativa solamente.

        // Build observations (solo identidad y licencia)
        List<String> obs = new ArrayList<>();
        if (!apiOk)               obs.add("No se obtuvo respuesta de la API");
        if (Boolean.FALSE.equals(coincideNombres))   obs.add("Nombres no coinciden con el documento");
        if (Boolean.FALSE.equals(coincideApellidos)) obs.add("Apellidos no coinciden con el documento");
        if (tieneConducir && !licVigente && !licData.isEmpty()) obs.add("Licencia no vigente");

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
                .licenciaNumero(licNumero)
                .licenciaCategoria(licCategoria)
                .licenciaEstado(licEstado)
                .licenciaVencimiento(licVencim)
                .coincideNombres(coincideNombres)
                .coincideApellidos(coincideApellidos)
                .licenciaVigente(licData.isEmpty() ? null : licVigente)
                .tieneConducir(tieneConducir)
                .verificadoPor(verificadoPor)
                .observaciones(obs.isEmpty() ? null : String.join("; ", obs))
                .exitoso(apiOk)
                .build();
    }

    // ── Persist result under clientes_v1/{id}.verificacionIdentidad ───────
    private Mono<VerificacionIdentidadResult> persistResult(
            String clienteId, VerificacionIdentidadResult result) {

        Map<String, Object> verMap = new HashMap<>();
        verMap.put("documentType",       result.getDocumentType());
        verMap.put("documentNumber",     result.getDocumentNumber());
        verMap.put("apiNombres",         result.getApiNombres());
        verMap.put("apiApellidoPaterno", result.getApiApellidoPaterno());
        verMap.put("apiApellidoMaterno", result.getApiApellidoMaterno());
        verMap.put("apiDireccion",       result.getApiDireccion());
        verMap.put("apiUbigeo",          result.getApiUbigeo());
        verMap.put("apiDepartamento",    result.getApiDepartamento());
        verMap.put("apiProvincia",       result.getApiProvincia());
        verMap.put("apiDistrito",        result.getApiDistrito());
        verMap.put("licenciaNumero",     result.getLicenciaNumero());
        verMap.put("licenciaCategoria",  result.getLicenciaCategoria());
        verMap.put("licenciaEstado",     result.getLicenciaEstado());
        verMap.put("licenciaVencimiento",result.getLicenciaVencimiento());
        verMap.put("coincideNombres",    result.getCoincideNombres());
        verMap.put("coincideApellidos",  result.getCoincideApellidos());
        verMap.put("licenciaVigente",    result.getLicenciaVigente());
        verMap.put("tieneConducir",      result.getTieneConducir());
        verMap.put("observaciones",      result.getObservaciones());
        verMap.put("exitoso",            result.isExitoso());
        verMap.put("verificadoPor",      result.getVerificadoPor());
        verMap.put("fechaVerificacion",  Timestamp.now());

        Map<String, Object> clienteUpdates = new HashMap<>();
        clienteUpdates.put("verificacionIdentidad", verMap);
        clienteUpdates.put("updatedAt", Timestamp.now());

        return clienteRepository.updateFields(clienteId, clienteUpdates)
                .thenReturn(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase()
                .replace("Á","A").replace("É","E").replace("Í","I")
                .replace("Ó","O").replace("Ú","U").replace("Ü","U")
                .replace("Ñ","N");
    }
}
