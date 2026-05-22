package com.motoyav2.cobranza.application.service;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.cobranza.application.port.in.RecibirVoucherUseCase;
import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import com.motoyav2.cobranza.application.port.out.WhatsAppSenderPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstadoConversacionDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherSueltoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.CasoCobranzaRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.EstadoConversacionRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.VoucherSueltoRepository;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherSueltoService {

    private final Firestore                      firestore;
    private final WhatsAppMediaStorageService    mediaStorageService;
    private final WhatsAppSenderPort             whatsAppSenderPort;
    private final VoucherSueltoRepository        voucherSueltoRepository;
    private final EstadoConversacionRepository   estadoConversacionRepository;
    private final CasoCobranzaRepository         casoCobranzaRepository;
    private final RecibirVoucherUseCase          recibirVoucherUseCase;

    private static final Set<String>    MEDIA_TIPOS      = Set.of("image", "document");
    private static final List<String>   CICLOS_INACTIVOS = List.of("PAGADO_TOTAL", "JUDICIAL", "CASTIGADO", "CERRADO");
    private static final long           EXPIRACION_MS    = 30L * 60 * 1000;
    private static final Pattern        PATRON_DNI       = Pattern.compile("\\b(\\d{8})\\b");
    private static final Pattern        PATRON_CE        = Pattern.compile("\\b([Cc][Ee][-\\s]?\\d{6,9})\\b");
    private static final Pattern        PATRON_PLACA     = Pattern.compile("\\b([A-Za-z]{3,4}[-\\s]?\\d{3}(?:\\d{1})?)\\b");

    private static final String MSG_PEDIR_DATOS =
        "Hola, recibimos tu comprobante de pago. Para registrarlo correctamente, " +
        "por favor indicanos los siguientes datos del titular del credito:\n\n" +
        "1. *Nombre completo* del titular\n" +
        "2. *Numero de documento* (DNI o Carnet de Extranjeria)\n" +
        "3. *Placa del vehiculo*\n\n" +
        "Puedes responder todo en un solo mensaje. Gracias.";
    private static final String         COL_SUELTOS      = "cobranzas-vouchers-sueltos";
    private static final String         COL_CASOS        = "cobranzas-casos";

    // ─── Punto de entrada desde el dispatcher ────────────────────────────────

    public Mono<Void> manejarMensajeDesconocido(String phone, String text, String mediaType, String mediaUrl) {
        String tel9 = toTelefono9(phone);
        if (tel9.isBlank()) return Mono.empty();

        return estadoConversacionRepository.findById(tel9)
            .flatMap(estado -> {
                if (estado.getExpiraEn() != null && estado.getExpiraEn().before(new Date())) {
                    return estadoConversacionRepository.deleteById(tel9)
                        .then(procesarMensajeNuevo(phone, tel9, text, mediaType, mediaUrl));
                }
                if (text != null && !text.isBlank()) {
                    return procesarRespuestaIdentificacion(phone, tel9, text, estado);
                }
                return Mono.empty();
            })
            .switchIfEmpty(procesarMensajeNuevo(phone, tel9, text, mediaType, mediaUrl));
    }

    // ─── API pública para el controller ──────────────────────────────────────

    public Flux<Map<String, Object>> listar(String estado) {
        Flux<VoucherSueltoDocument> docs = (estado != null && !estado.isBlank())
            ? voucherSueltoRepository.findByEstado(estado)
            : voucherSueltoRepository.findAll();
        return docs.flatMap(this::toResponse);
    }

    public Mono<Map<String, Object>> obtener(String id) {
        return voucherSueltoRepository.findById(id).flatMap(this::toResponse);
    }

    public Mono<String> asociar(String id, String contratoId, boolean guardarTelefono, String agenteId) {
        return voucherSueltoRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Voucher suelto no encontrado: " + id)))
            .flatMap(doc -> {
                if (!"PENDIENTE".equals(doc.getEstado())) {
                    return Mono.error(new IllegalStateException("El voucher ya fue procesado: " + doc.getEstado()));
                }
                return casoCobranzaRepository.findById(contratoId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Contrato no encontrado: " + contratoId)))
                    .flatMap(caso -> asociarInterno(doc, caso, agenteId)
                        .flatMap(voucherId -> {
                            if (guardarTelefono && doc.getTelefono() != null && !doc.getTelefono().isBlank()) {
                                return agregarTelefonoAdicional(contratoId, doc.getTelefono())
                                    .thenReturn(voucherId);
                            }
                            return Mono.just(voucherId);
                        }));
            });
    }

    public Mono<Void> descartar(String id, String motivo) {
        return Mono.fromCallable(() -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("estado", "DESCARTADO");
            updates.put("motivoDescarte", motivo != null ? motivo : "");
            firestore.collection(COL_SUELTOS).document(id).update(updates).get();
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ─── Lógica interna ───────────────────────────────────────────────────────

    private Mono<Void> procesarMensajeNuevo(String phone, String tel9, String text,
                                             String mediaType, String mediaUrl) {
        if (mediaUrl != null && mediaType != null && MEDIA_TIPOS.contains(mediaType.toLowerCase())) {
            return procesarMediaDesconocida(phone, tel9, mediaType, mediaUrl);
        }
        return Mono.empty();
    }

    private Mono<Void> procesarMediaDesconocida(String phone, String tel9, String mediaType, String mediaUrl) {
        String id = UUID.randomUUID().toString();
        log.info("[VOUCHER-SUELTO] Imagen de número desconocido phone={} id={}", phone, id);

        return mediaStorageService.subirDesdeUrl(mediaUrl, mediaType, id)
            .flatMap(result -> {
                VoucherSueltoDocument doc = VoucherSueltoDocument.builder()
                    .id(id)
                    .telefono(tel9)
                    .telefonoRaw(phone)
                    .gcsPath(result.gcsPath())
                    .mediaType(mediaType)
                    .estado("PENDIENTE")
                    .recibidoEn(new Date())
                    .build();
                return voucherSueltoRepository.save(doc);
            })
            .flatMap(doc -> {
                Date now = new Date();
                EstadoConversacionDocument estado = EstadoConversacionDocument.builder()
                    .telefono(tel9)
                    .estado("ESPERANDO_DATOS")
                    .voucherSueltoId(id)
                    .creadoEn(now)
                    .expiraEn(new Date(now.getTime() + EXPIRACION_MS))
                    .build();
                return estadoConversacionRepository.save(estado);
            })
            .flatMap(e -> whatsAppSenderPort.enviarTexto(phone, MSG_PEDIR_DATOS))
            .onErrorResume(e -> {
                log.warn("[VOUCHER-SUELTO] Error procesando media de {}: {}", phone, e.getMessage());
                return Mono.empty();
            })
            .then();
    }

    private Mono<Void> procesarRespuestaIdentificacion(String phone, String tel9, String text,
                                                        EstadoConversacionDocument estado) {
        Optional<String> dniOpt   = extraerDni(text);
        Optional<String> ceOpt    = extraerCe(text);
        Optional<String> placaOpt = extraerPlaca(text);
        // Documento de identidad: prioriza DNI, si no hay intenta CE
        Optional<String> docOpt   = dniOpt.isPresent() ? dniOpt : ceOpt;
        String voucherSueltoId    = estado.getVoucherSueltoId();

        Mono<Void> guardarDatos = Mono.fromCallable(() -> {
            Map<String, Object> datos = new HashMap<>();
            datos.put("textoRecibido", text);
            docOpt.ifPresent(doc -> datos.put("documento", doc));
            dniOpt.ifPresent(dni -> datos.put("dni", dni));
            ceOpt.ifPresent(ce  -> datos.put("ce", ce));
            placaOpt.ifPresent(p -> datos.put("placa", p));
            firestore.collection(COL_SUELTOS).document(voucherSueltoId)
                .update("datosProporcionados", datos).get();
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();

        Mono<Void> limpiarEstado = estadoConversacionRepository.deleteById(tel9).then();
        String msgGracias = "Gracias por la informacion. Un asesor revisara tu comprobante a la brevedad.";

        if (docOpt.isEmpty()) {
            return guardarDatos.then(limpiarEstado)
                .then(whatsAppSenderPort.enviarTexto(phone, msgGracias)).then();
        }

        return guardarDatos
            .then(casoCobranzaRepository.findByClienteDni(docOpt.get())
                .filter(c -> c.getCicloVida() == null || !CICLOS_INACTIVOS.contains(c.getCicloVida()))
                .next())
            .flatMap(caso -> {
                log.info("[VOUCHER-SUELTO] Auto-match doc={} → contratoId={}", docOpt.get(), caso.getContratoId());
                return voucherSueltoRepository.findById(voucherSueltoId)
                    .flatMap(doc -> asociarInterno(doc, caso, "SISTEMA"))
                    .then(limpiarEstado)
                    .then(whatsAppSenderPort.enviarTexto(phone,
                        "Gracias. Tu comprobante fue registrado para el contrato de " +
                        caso.getClienteNombre() + ". Un asesor lo revisara."));
            })
            .switchIfEmpty(limpiarEstado.then(whatsAppSenderPort.enviarTexto(phone, msgGracias)))
            .then();
    }

    private Mono<String> asociarInterno(VoucherSueltoDocument doc, CasoCobranzaDocument caso, String agenteId) {
        RecibirVoucherCommand cmd = new RecibirVoucherCommand(
            caso.getContratoId(),
            caso.getStoreId(),
            doc.getGcsPath(),
            null,
            null,
            null,
            null,
            agenteId != null ? agenteId : "SISTEMA",
            "WHATSAPP",
            caso.getClienteNombre(),
            doc.getMediaType()
        );
        return recibirVoucherUseCase.ejecutar(cmd)
            .flatMap(voucherId -> Mono.fromCallable(() -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("estado", "ASOCIADO");
                updates.put("contratoId", caso.getContratoId());
                updates.put("storeId", caso.getStoreId());
                updates.put("clienteNombre", caso.getClienteNombre());
                updates.put("voucherGeneradoId", voucherId);
                updates.put("asociadoPor", agenteId != null ? agenteId : "SISTEMA");
                updates.put("asociadoEn", new Date());
                firestore.collection(COL_SUELTOS).document(doc.getId()).update(updates).get();
                return voucherId;
            }).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<Void> agregarTelefonoAdicional(String contratoId, String tel9) {
        return Mono.fromCallable(() -> {
            firestore.collection(COL_CASOS).document(contratoId)
                .update("telefonosAdicionales", FieldValue.arrayUnion(tel9)).get();
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Map<String, Object>> toResponse(VoucherSueltoDocument doc) {
        Map<String, Object> r = new HashMap<>();
        r.put("id",                   doc.getId());
        r.put("telefono",             doc.getTelefono());
        r.put("telefonoRaw",          doc.getTelefonoRaw());
        r.put("mediaType",            doc.getMediaType());
        r.put("estado",               doc.getEstado());
        r.put("contratoId",           doc.getContratoId());
        r.put("clienteNombre",        doc.getClienteNombre());
        r.put("datosProporcionados",  doc.getDatosProporcionados());
        r.put("storeId",              doc.getStoreId());
        r.put("voucherGeneradoId",    doc.getVoucherGeneradoId());
        r.put("asociadoPor",          doc.getAsociadoPor());
        r.put("motivoDescarte",       doc.getMotivoDescarte());
        if (doc.getRecibidoEn() != null) r.put("recibidoEn", doc.getRecibidoEn().getTime());
        if (doc.getAsociadoEn() != null) r.put("asociadoEn", doc.getAsociadoEn().getTime());

        if (doc.getGcsPath() != null && !doc.getGcsPath().isBlank()) {
            return mediaStorageService.generarSignedUrl(doc.getGcsPath(), 15)
                .doOnNext(url -> r.put("imagenUrl", url))
                .thenReturn(r);
        }
        return Mono.just(r);
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private Optional<String> extraerDni(String text) {
        if (text == null) return Optional.empty();
        Matcher m = PATRON_DNI.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Optional<String> extraerCe(String text) {
        if (text == null) return Optional.empty();
        Matcher m = PATRON_CE.matcher(text);
        return m.find() ? Optional.of(m.group(1).toUpperCase().replaceAll("\\s", "")) : Optional.empty();
    }

    private Optional<String> extraerPlaca(String text) {
        if (text == null) return Optional.empty();
        Matcher m = PATRON_PLACA.matcher(text);
        return m.find() ? Optional.of(m.group(1).toUpperCase().replaceAll("[-\\s]", "")) : Optional.empty();
    }

    private String toTelefono9(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        if (digits.length() == 9) return digits;
        return "";
    }
}
