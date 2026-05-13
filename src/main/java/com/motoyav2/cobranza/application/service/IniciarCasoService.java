package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class IniciarCasoService implements IniciarCasoUseCase {

    /** Clientes migrados sin tienda de origen pertenecen al pool de cobranzas. */
    public static final String STORE_COBRANZAS = "COBRANZAS";

    private final CasoCobranzaPort casoPort;
    private final EventoCobranzaPort eventoPort;

    @Override
    public Mono<CasoCobranzaDocument> ejecutar(IniciarCasoCommand command) {
        return casoPort.findById(command.contratoId())
                .defaultIfEmpty(new CasoCobranzaDocument())
                .flatMap(existente -> {
                    boolean esNuevo = existente.getContratoId() == null;

                    int cuotasTotales = command.cronograma() != null ? command.cronograma().size() : 0;
                    int cuotasPagadas = command.cronograma() != null
                            ? (int) command.cronograma().stream()
                                    .filter(c -> "PAGADA".equalsIgnoreCase(c.getEstado())).count()
                            : 0;
                    // totalPagado calculado desde el cronograma para reflejar cuotas ya pagadas en la importación
                    double totalPagadoCronograma = command.cronograma() != null
                            ? command.cronograma().stream()
                                    .filter(c -> "PAGADA".equalsIgnoreCase(c.getEstado()))
                                    .mapToDouble(c -> c.getMonto() != null ? c.getMonto() : 0.0)
                                    .sum()
                            : 0.0;

                    // Clientes migrados sin tienda → asignar al pool de cobranzas
                    String storeId = (command.storeId() != null && !command.storeId().isBlank())
                            ? command.storeId() : STORE_COBRANZAS;

                    CasoCobranzaDocument doc = CasoCobranzaDocument.builder()
                            .contratoId(command.contratoId())
                            .storeId(storeId)
                            .titular(command.titular())
                            .fiador(command.fiador())
                            // Campos planos para queries (denormalizados)
                            .clienteNombre(command.titular() != null
                                    ? command.titular().nombreCompleto() : null)
                            .clienteTelefono(command.titular() != null
                                    ? command.titular().getTelefono() : null)
                            .clienteDni(command.titular() != null
                                    ? command.titular().getNumeroDocumento() : null)
                            .motoDescripcion(command.motoDescripcion())
                            .capitalOriginal(command.capitalOriginal())
                            .saldoActual(command.saldoActual())
                            .nivelEstrategia(command.nivelEstrategia() != null
                                    ? command.nivelEstrategia() : "AL_DIA")
                            .estadoCaso(command.estadoCaso() != null
                                    ? command.estadoCaso() : "EN_SEGUIMIENTO")
                            .cicloVida(esNuevo ? "ACTIVO"
                                    : (existente.getCicloVida() != null ? existente.getCicloVida() : "ACTIVO"))
                            .agenteAsignadoId(command.agenteAsignadoId())
                            .agenteAsignadoNombre(command.agenteAsignadoNombre())
                            .fechaVencimientoPrimerCuotaImpaga(parseFecha(command.fechaVencimientoPrimerCuotaImpaga()))
                            .cronograma(command.cronograma())
                            .numeroCuotasTotales(cuotasTotales > 0 ? cuotasTotales
                                    : (existente.getNumeroCuotasTotales() != null ? existente.getNumeroCuotasTotales() : 0))
                            .numeroCuotasPagadas(esNuevo ? cuotasPagadas
                                    : (existente.getNumeroCuotasPagadas() != null ? existente.getNumeroCuotasPagadas() : cuotasPagadas))
                            .totalMora(esNuevo ? 0.0
                                    : (existente.getTotalMora() != null ? existente.getTotalMora() : 0.0))
                            .totalPagado(esNuevo ? totalPagadoCronograma
                                    : (existente.getTotalPagado() != null ? existente.getTotalPagado() : totalPagadoCronograma))
                            .totalCondonado(esNuevo ? 0.0
                                    : (existente.getTotalCondonado() != null ? existente.getTotalCondonado() : 0.0))
                            .mensajesNoLeidos(esNuevo ? 0
                                    : (existente.getMensajesNoLeidos() != null ? existente.getMensajesNoLeidos() : 0))
                            .creadoEn(esNuevo ? new Date() : existente.getCreadoEn())
                            .actualizadoEn(new Date())
                            .creadoPor(esNuevo ? command.creadoPor() : existente.getCreadoPor())
                            .actualizadoPor(command.creadoPor())
                            .build();

                    return casoPort.save(doc)
                            .flatMap(saved -> {
                                // Solo registrar CASO_INICIADO cuando el caso es realmente nuevo;
                                // una invocación duplicada (esNuevo=false) no genera evento para
                                // evitar falsos CASO_ACTUALIZADO por race conditions en aprobación.
                                if (!esNuevo) return reactor.core.publisher.Mono.just(saved);
                                return eventoPort.append(saved.getContratoId(),
                                        EventoCobranzaDocument.builder()
                                                .contratoId(saved.getContratoId())
                                                .tipo("CASO_INICIADO")
                                                .payload(Map.of(
                                                        "clienteNombre", saved.getClienteNombre() != null ? saved.getClienteNombre() : "",
                                                        "saldoActual", saved.getSaldoActual() != null ? saved.getSaldoActual() : 0.0,
                                                        "nivelEstrategia", saved.getNivelEstrategia() != null ? saved.getNivelEstrategia() : ""
                                                ))
                                                .usuarioId(command.creadoPor())
                                                .usuarioNombre(command.creadoPor())
                                                .automatico(false)
                                                .creadoEn(new Date())
                                                .build()
                                ).thenReturn(saved);
                            });
                });
    }

    /** Convierte ISO date string ("YYYY-MM-DD" o "YYYY-MM-DDTHH:mm:ssZ") a Date. */
    private Date parseFecha(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            if (iso.contains("T")) {
                return Date.from(java.time.Instant.parse(iso));
            }
            return Date.from(java.time.LocalDate.parse(iso)
                    .atStartOfDay(ZoneId.of("America/Lima")).toInstant());
        } catch (Exception e) {
            return null;
        }
    }
}
