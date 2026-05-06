package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import com.motoyav2.evaluacion.application.dto.IngresarSolicitudResult;
import com.motoyav2.evaluacion.domain.port.in.DetectarDuplicadosUseCase;
import com.motoyav2.evaluacion.domain.port.in.EnviarBienvenidaWhatsAppUseCase;
import com.motoyav2.evaluacion.domain.port.in.EnviarVerificacionWhatsAppUseCase;
import com.motoyav2.evaluacion.domain.port.in.IngresarSolicitudUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.port.out.VehiculoRepository;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngresarSolicitudUseCaseImpl implements IngresarSolicitudUseCase {

    private final ClienteRepository clienteRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ReferenciaRepository referenciaRepository;
    private final SolicitudRepository solicitudRepository;
    private final NotificationFacade notificationFacade;
    private final EnviarBienvenidaWhatsAppUseCase enviarBienvenida;
    private final EnviarVerificacionWhatsAppUseCase enviarVerificacion;
    private final DetectarDuplicadosUseCase detectarDuplicados;
    private final com.motoyav2.evaluacion.domain.port.out.PersonaRepository personaRepository;
    private final com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository.VendedorProfileRepository vendedorProfileRepository;

    @Override
    public Mono<IngresarSolicitudResult> ejecutar(IngresarSolicitudCommand cmd) {

        // Validación temprana: titular y fiador no pueden ser la misma persona
        if (cmd.fiador() != null
                && cmd.titular().documentNumber() != null
                && cmd.titular().documentNumber().equalsIgnoreCase(cmd.fiador().documentNumber())) {
            return Mono.error(new com.motoyav2.shared.exception.BadRequestException(
                    "El titular y el fiador no pueden tener el mismo documento de identidad ("
                    + cmd.titular().documentNumber() + ")"));
        }

        String codigoDeSolicitud = generarCodigo();
        Timestamp ahora = Timestamp.now();

        // 1. Crear snapshot inmutable del titular (NUNCA se reutiliza/sobreescribe un doc existente)
        Mono<String> titularIdMono = crearClienteSnapshot(cmd.titular(), "titular", codigoDeSolicitud, ahora);

        return titularIdMono.flatMap(titularId -> {

            // 2. Crear snapshot inmutable del fiador (si existe)
            Mono<String> fiadorIdMono = cmd.fiador() != null
                    ? crearClienteSnapshot(cmd.fiador(), "fiador", codigoDeSolicitud, ahora)
                    : Mono.just("");

            return fiadorIdMono.flatMap(fiadorId -> {

                // 3. Crear vehículo
                Map<String, Object> vehiculoMap = buildVehiculoMap(cmd.vehiculo(), codigoDeSolicitud, ahora);
                Mono<String> vehiculoIdMono = vehiculoRepository.create(vehiculoMap);

                return vehiculoIdMono.flatMap(vehiculoId -> {

                    // 4. Crear referencias en paralelo
                    AtomicInteger idx = new AtomicInteger(1);
                    Mono<List<String>> referenciasIdsMono = Flux.fromIterable(cmd.referencias())
                            .flatMap(ref -> {
                                Map<String, Object> refMap = buildReferenciaMap(
                                        ref, titularId, idx.getAndIncrement(), codigoDeSolicitud, ahora);
                                return referenciaRepository.create(refMap);
                            })
                            .collectList();

                    return referenciasIdsMono.flatMap(referenciasIds -> {

                        // Resolver tiendaId correcto: si el form no lo envió, buscar en vendedor_profiles
                        IngresarSolicitudCommand.VendedorData vend = cmd.vendedor();
                        Mono<String> tiendaIdResolvedMono =
                                (vend.tienda() == null || vend.tienda().isBlank())
                                        ? vendedorProfileRepository.findById(vend.id())
                                                .map(vp -> vp.getTiendaId() != null ? vp.getTiendaId() : "")
                                                .defaultIfEmpty("")
                                        : Mono.just(vend.tienda());

                        return tiendaIdResolvedMono.flatMap(resolvedTiendaId -> {

                        // 5. Crear solicitud
                        Map<String, Object> solMap = buildSolicitudMap(
                                cmd, titularId,
                                fiadorId.isEmpty() ? null : fiadorId,
                                vehiculoId, referenciasIds,
                                codigoDeSolicitud, ahora, resolvedTiendaId);

                        return solicitudRepository.create(solMap)
                                .flatMap(solicitudId -> {
                                    log.info("Solicitud ingresada: id={} codigo={}", solicitudId, codigoDeSolicitud);

                                    // 6. Fire-and-forget: MX check de emails + notificaciones
                                    dispararPostIngreso(solicitudId, titularId, fiadorId, referenciasIds, cmd, ahora, codigoDeSolicitud);

                                    return Mono.just(new IngresarSolicitudResult(
                                            solicitudId, codigoDeSolicitud, "pendiente"));
                                });
                        }); // tiendaIdResolvedMono
                    }); // referenciasIdsMono
                });
            });
        });
    }

    /**
     * Ejecuta en paralelo (fire-and-forget):
     *   a) MX check del email del titular y actualiza validacionEmail en su doc
     *   b) MX check del email del fiador y actualiza validacionEmail en su doc
     *   c) Publica eventos de notificación al Outbox (titular + fiador + vendedor)
     *
     * Ningún error aquí propaga al flujo principal — la solicitud ya fue guardada.
     */
    private void dispararPostIngreso(String solicitudId, String titularId, String fiadorId,
                                      List<String> referenciasIds,
                                      IngresarSolicitudCommand cmd, Timestamp ahora,
                                      String codigoDeSolicitud) {

        String emailTitular = cmd.titular().email();
        String emailFiador  = cmd.fiador() != null ? cmd.fiador().email() : null;

        // MX check titular
        Mono<Void> mxTitular = checkEmail(emailTitular)
                .flatMap(result -> clienteRepository.updateFields(titularId,
                        Map.of("validacionEmail", result, "updatedAt", ahora)))
                .doOnError(e -> log.warn("[MX] Error actualizando validacionEmail titular {}: {}", titularId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());

        // MX check fiador (solo si existe)
        Mono<Void> mxFiador = (!fiadorId.isEmpty() && emailFiador != null && !emailFiador.isBlank())
                ? checkEmail(emailFiador)
                        .flatMap(result -> clienteRepository.updateFields(fiadorId,
                                Map.of("validacionEmail", result, "updatedAt", ahora)))
                        .doOnError(e -> log.warn("[MX] Error actualizando validacionEmail fiador {}: {}", fiadorId, e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                : Mono.empty();

        // Notificaciones (Outbox)
        String nombreTitular = buildNombreCompleto(cmd.titular());
        String nombreFiador  = cmd.fiador() != null ? buildNombreCompleto(cmd.fiador()) : null;
        String modeloMoto    = cmd.vehiculo().marca() + " " + cmd.vehiculo().modelo();
        String monto         = cmd.financiamiento().precioCompraMoto() != null
                ? "S/ " + String.format("%.2f", cmd.financiamiento().precioCompraMoto())
                : "";
        String fechaRegistro = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        Mono<Void> notif = notificationFacade.notificarSolicitudIngresada(
                solicitudId,
                emailTitular,               nombreTitular,
                emailFiador,                nombreFiador,
                cmd.vendedor().email(),     cmd.vendedor().nombre(),
                modeloMoto,                 monto,
                cmd.titular().documentNumber(), cmd.titular().telefono1(),
                fechaRegistro,              codigoDeSolicitud)
                .doOnError(e -> log.warn("[NOTIF] Error publicando evento solicitud ingresada {}: {}", solicitudId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());

        // Upsert registro maestro en `personas` (para autocomplete y central de riesgo)
        Mono<Void> personaTitular = personaRepository.upsert(
                        cmd.titular().documentNumber(), buildPersonaMap(cmd.titular(), ahora))
                .doOnError(e -> log.warn("[PERSONAS] Error upsert titular {}: {}", cmd.titular().documentNumber(), e.getMessage()))
                .onErrorResume(e -> Mono.empty());

        Mono<Void> personaFiador = (cmd.fiador() != null)
                ? personaRepository.upsert(cmd.fiador().documentNumber(), buildPersonaMap(cmd.fiador(), ahora))
                        .doOnError(e -> log.warn("[PERSONAS] Error upsert fiador {}: {}", cmd.fiador().documentNumber(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                : Mono.empty();

        // Bienvenida WhatsApp al titular
        String telefonoTitular = cmd.titular().telefono1();
        Mono<Void> bienvenidaTitular = (telefonoTitular != null && !telefonoTitular.isBlank())
                ? enviarBienvenida.enviar(solicitudId, telefonoTitular, nombreTitular, false)
                        .onErrorResume(e -> { log.warn("[BIENVENIDA] Error titular {}: {}", solicitudId, e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        // Bienvenida WhatsApp al fiador
        String telefonoFiador2 = cmd.fiador() != null ? cmd.fiador().telefono1() : null;
        Mono<Void> bienvenidaFiador = (!fiadorId.isEmpty() && telefonoFiador2 != null && !telefonoFiador2.isBlank())
                ? enviarBienvenida.enviar(solicitudId, telefonoFiador2, nombreFiador != null ? nombreFiador : "", true)
                        .onErrorResume(e -> { log.warn("[BIENVENIDA] Error fiador {}: {}", solicitudId, e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        // Detección de duplicados / riesgo compartido
        String fiadorDni = cmd.fiador() != null ? cmd.fiador().documentNumber() : null;
        Mono<Void> duplicados = detectarDuplicados.detectar(solicitudId, cmd.titular().documentNumber(), fiadorDni)
                .onErrorResume(e -> { log.warn("[DUPLICADOS] Error solicitud={}: {}", solicitudId, e.getMessage()); return Mono.empty(); });

        // WA de verificación a TODAS las referencias (automático al ingresar solicitud)
        Mono<Void> waReferencias = (referenciasIds != null && !referenciasIds.isEmpty())
                ? Flux.fromIterable(referenciasIds)
                        .flatMap(refId -> enviarVerificacion.ejecutar(refId, solicitudId)
                                .onErrorResume(e -> {
                                    log.warn("[WA-REF] Error enviando verificación a refId={}: {}", refId, e.getMessage());
                                    return Mono.empty();
                                }))
                        .then()
                : Mono.empty();

        Mono.when(mxTitular, mxFiador, notif, personaTitular, personaFiador, bienvenidaTitular, bienvenidaFiador, waReferencias, duplicados)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        e -> log.warn("[POST-INGRESO] Error en tarea post-ingreso solicitud={}: {}", solicitudId, e.getMessage())
                );
    }

    /**
     * Valida el email en dos niveles:
     *   1. Sintaxis básica (RFC 5322 simplificado)
     *   2. MX record DNS — el dominio tiene servidores de correo
     *
     * Devuelve un Map para guardar directamente en Firestore como campo validacionEmail.
     * javax.naming es parte del JDK estándar (módulo java.naming) — no deprecado en Java 21.
     */
    private Mono<Map<String, Object>> checkEmail(String email) {
        if (email == null || email.isBlank()) {
            return Mono.just(buildValidacionResult(false, "EMAIL_VACIO", "No se proporcionó email"));
        }

        // Sintaxis
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return Mono.just(buildValidacionResult(false, "SINTAXIS_INVALIDA",
                    "El email no tiene formato válido"));
        }

        String domain = email.substring(email.lastIndexOf('@') + 1);

        // MX lookup — bloqueante, ejecutar en boundedElastic
        return Mono.fromCallable(() -> {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns://");
            try {
                DirContext ctx = new InitialDirContext(env);
                Attributes attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
                Attribute mx = attrs.get("MX");
                ctx.close();
                if (mx != null && mx.size() > 0) {
                    return buildValidacionResult(true, "MX_OK",
                            "El dominio tiene servidores de correo configurados");
                } else {
                    return buildValidacionResult(false, "DOMINIO_SIN_MX",
                            "El dominio existe pero no tiene servidores de correo");
                }
            } catch (NamingException e) {
                return buildValidacionResult(false, "DOMINIO_NO_ENCONTRADO",
                        "El dominio no existe o no se pudo resolver: " + domain);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> buildValidacionResult(boolean valido, String nivel, String detalle) {
        Map<String, Object> m = new HashMap<>();
        m.put("valido", valido);
        m.put("nivel", nivel);
        m.put("detalle", detalle);
        m.put("verificadoEn", Timestamp.now());
        return m;
    }

    private String buildNombreCompleto(IngresarSolicitudCommand.ClienteData d) {
        return (d.nombres() + " " + d.apellidoPaterno() + " " + d.apellidoMaterno()).trim();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Crea siempre un nuevo documento snapshot para el cliente en esta solicitud.
     * NUNCA se reutiliza ni se sobreescribe un documento existente — cada solicitud
     * tiene su propio snapshot inmutable del cliente en el momento del ingreso.
     * Esto garantiza que cambios en solicitudes futuras no alteren datos históricos.
     */
    private Mono<String> crearClienteSnapshot(IngresarSolicitudCommand.ClienteData data,
                                               String tipo, String codigoDeSolicitud, Timestamp ahora) {
        return clienteRepository.create(buildClienteMap(data, tipo, codigoDeSolicitud, ahora));
    }

    private Map<String, Object> buildClienteMap(IngresarSolicitudCommand.ClienteData d,
                                                  String tipo, String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipo", tipo);
        m.put("tipoCliente", tipo);
        m.put("codigoDeSolicitud", codigo);
        m.put("documentType", d.documentType());
        m.put("documentNumber", d.documentNumber());
        m.put("nombres", d.nombres());
        m.put("apellidoPaterno", d.apellidoPaterno());
        m.put("apellidoMaterno", d.apellidoMaterno());
        m.put("estadoCivil", d.estadoCivil());
        m.put("email", d.email());
        m.put("fechaNacimiento", d.fechaNacimiento());
        m.put("departamento", d.departamento());
        m.put("provincia", d.provincia());
        m.put("distrito", d.distrito());
        m.put("direccion", d.direccion());
        m.put("ubicacionGPSCasa", d.ubicacionGPSCasa());
        m.put("telefono1", normalizarTelefono(d.telefono1()));
        m.put("telefono2", normalizarTelefono(d.telefono2()));
        m.put("ocupacion", d.ocupacion());
        m.put("rangoIngresos", d.rangoIngresos());
        m.put("tipoVivienda", d.tipoVivienda());
        m.put("licenciaConducir", d.licenciaConducir());
        m.put("numeroLicencia", d.numeroLicencia());
        if (d.sexo()             != null) m.put("sexo",             d.sexo());
        if (d.tipoTrabajo()      != null) m.put("tipoTrabajo",      d.tipoTrabajo());
        if (d.relacionConFiador()  != null) m.put("relacionConFiador",  d.relacionConFiador());
        if (d.relacionConTitular() != null) m.put("relacionConTitular", d.relacionConTitular());
        if (d.nacionalidad()     != null) m.put("nacionalidad",     d.nacionalidad());
        if (d.estadoResidencia() != null) m.put("estadoResidencia", d.estadoResidencia());
        if (d.archivos() != null && !d.archivos().isEmpty()) {
            m.put("archivos", d.archivos());
        }
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private Map<String, Object> buildVehiculoMap(IngresarSolicitudCommand.VehiculoData d,
                                                   String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("marca", d.marca());
        m.put("modelo", d.modelo());
        m.put("color", d.color());
        m.put("anio", d.anio());
        m.put("codigoDeSolicitud", codigo);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private Map<String, Object> buildReferenciaMap(IngresarSolicitudCommand.ReferenciaData d,
                                                     String titularId, int numero,
                                                     String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", d.nombre());
        m.put("apellidos", d.apellidos());
        m.put("telefono", normalizarTelefono(d.telefono()));
        m.put("parentesco", d.parentesco());
        m.put("titularId", titularId);
        m.put("numero", numero);
        m.put("codigoDeSolicitud", codigo);
        m.put("estadoVerificacion", "pendiente");
        m.put("rechazada", false);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private Map<String, Object> buildSolicitudMap(IngresarSolicitudCommand cmd,
                                                   String titularId, String fiadorId,
                                                   String vehiculoId, List<String> referenciasIds,
                                                   String codigo, Timestamp ahora,
                                                   String resolvedTiendaId) {
        Map<String, Object> m = new HashMap<>();
        m.put("codigoDeSolicitud", codigo);
        m.put("estado", "pendiente");
        m.put("prioridad", "Media");
        m.put("titularId", titularId);
        // Desnormalización para listados rápidos
        String nombreCompleto = (cmd.titular().nombres() + " "
                + cmd.titular().apellidoPaterno() + " "
                + cmd.titular().apellidoMaterno()).trim();
        m.put("titularNombreCompleto", nombreCompleto);
        m.put("titularDni", cmd.titular().documentNumber());
        m.put("titularTelefono", normalizarTelefono(cmd.titular().telefono1()));
        m.put("titularEmail", cmd.titular().email());
        m.put("fiadorId", fiadorId);
        // Desnormalización del DNI y teléfono del fiador
        if (cmd.fiador() != null && cmd.fiador().documentNumber() != null) {
            m.put("fiadorDni", cmd.fiador().documentNumber());
        }
        if (cmd.fiador() != null && cmd.fiador().telefono1() != null) {
            m.put("fiadorTelefono", normalizarTelefono(cmd.fiador().telefono1()));
        }
        m.put("vehiculoId", vehiculoId);
        m.put("referenciasIds", referenciasIds);

        // Financiero (campos legacy — compatibilidad con listados)
        IngresarSolicitudCommand.FinanciamientoData fin = cmd.financiamiento();
        m.put("precioCompraMoto", fin.precioCompraMoto());
        m.put("inicial", fin.inicial());
        m.put("plazoQuincenas", fin.plazoQuincenas());
        m.put("montoCuota", fin.montoCuota());

        // Resolver montoFinanciar: si el frontend lo envía úsalo; si no, calcularlo
        double gastosAdmin = fin.gastosAdministrativos() != null ? fin.gastosAdministrativos() : 0.0;
        double montoFinanciarReal = fin.montoFinanciar() != null
                ? fin.montoFinanciar()
                : (fin.precioCompraMoto() + gastosAdmin - fin.inicial());
        double costoTotal = fin.precioCompraMoto() + gastosAdmin;

        // Sub-map datosFinancieros — necesario para que el evaluador vea montoFinanciar correcto
        Map<String, Object> dfMap = new HashMap<>();
        dfMap.put("montoVehiculo", fin.precioCompraMoto());
        dfMap.put("soatCostosNotariales", gastosAdmin > 0 ? gastosAdmin : null);
        dfMap.put("costoTotal", costoTotal);
        dfMap.put("inicial", fin.inicial());
        dfMap.put("montoFinanciar", montoFinanciarReal);
        dfMap.put("numeroCuotasQuincenales", fin.plazoQuincenas());
        dfMap.put("montoCuotaQuincenal", fin.montoCuota());
        if (fin.tea() != null)           dfMap.put("tea", fin.tea());
        if (fin.tcea() != null)          dfMap.put("tcea", fin.tcea());
        if (fin.tasaQuincenal() != null) dfMap.put("tasaQuincenal", fin.tasaQuincenal());
        m.put("datosFinancieros", dfMap);

        // Vendedor — incluye email para trazabilidad
        IngresarSolicitudCommand.VendedorData v = cmd.vendedor();
        m.put("vendedorId", v.id());
        m.put("vendedorNombre", v.nombre());
        Map<String, Object> vendedorMap = new HashMap<>();
        vendedorMap.put("id", v.id());
        vendedorMap.put("nombre", v.nombre());
        vendedorMap.put("tienda", resolvedTiendaId);
        if (v.email() != null && !v.email().isBlank()) vendedorMap.put("email", v.email());
        m.put("vendedor", vendedorMap);
        m.put("mensajeOpcional", cmd.mensajeOpcional());

        m.put("certificadoGenerado", false);
        m.put("contratoGenerado", false);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    /**
     * Construye el mapa de campos básicos para el registro maestro en `personas`.
     * Solo incluye datos de contacto — nunca evaluaciones ni documentos.
     */
    private Map<String, Object> buildPersonaMap(IngresarSolicitudCommand.ClienteData d, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("documentType", d.documentType());
        m.put("documentNumber", d.documentNumber());
        m.put("nombres", d.nombres());
        m.put("apellidoPaterno", d.apellidoPaterno());
        m.put("apellidoMaterno", d.apellidoMaterno());
        m.put("estadoCivil", d.estadoCivil());
        m.put("email", d.email());
        m.put("fechaNacimiento", d.fechaNacimiento());
        m.put("departamento", d.departamento());
        m.put("provincia", d.provincia());
        m.put("distrito", d.distrito());
        m.put("direccion", d.direccion());
        m.put("telefono1", normalizarTelefono(d.telefono1()));
        m.put("telefono2", normalizarTelefono(d.telefono2()));
        m.put("ocupacion", d.ocupacion());
        m.put("rangoIngresos", d.rangoIngresos());
        m.put("tipoVivienda", d.tipoVivienda());
        m.put("licenciaConducir", d.licenciaConducir());
        m.put("numeroLicencia", d.numeroLicencia());
        if (d.sexo()             != null) m.put("sexo",             d.sexo());
        if (d.tipoTrabajo()      != null) m.put("tipoTrabajo",      d.tipoTrabajo());
        if (d.relacionConFiador()  != null) m.put("relacionConFiador",  d.relacionConFiador());
        if (d.relacionConTitular() != null) m.put("relacionConTitular", d.relacionConTitular());
        if (d.nacionalidad()     != null) m.put("nacionalidad",     d.nacionalidad());
        if (d.estadoResidencia() != null) m.put("estadoResidencia", d.estadoResidencia());
        m.put("updatedAt", ahora);
        return m;
    }

    /**
     * Garantiza que el teléfono tenga el prefijo +51.
     * Ejemplos:
     *   957311203      → +51957311203
     *   51957311203    → +51957311203
     *   +51957311203   → +51957311203
     *   null / vacío   → se devuelve tal cual
     */
    static String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return telefono;
        String t = telefono.trim();
        if (t.startsWith("+51")) return t;
        if (t.startsWith("51") && t.length() == 11) return "+" + t;
        // 9 dígitos sin código de país
        if (t.matches("[0-9]{9}")) return "+51" + t;
        // Cualquier otro caso: añadir +51 al inicio si solo contiene dígitos
        if (t.matches("[0-9]+")) return "+51" + t;
        return t;
    }

    private String generarCodigo() {
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String alfa = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(alfa.charAt((int) (Math.random() * alfa.length())));
        }
        return "MDCR-" + fecha + "-" + sb;
    }
}
