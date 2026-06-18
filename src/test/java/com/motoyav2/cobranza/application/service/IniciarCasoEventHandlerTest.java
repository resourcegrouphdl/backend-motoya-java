package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.contrato.domain.event.ContratoActivadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IniciarCasoEventHandlerTest {

    private IniciarCasoUseCase iniciarCasoUseCase;
    private IniciarCasoEventHandler handler;

    @BeforeEach
    void setUp() {
        iniciarCasoUseCase = mock(IniciarCasoUseCase.class);
        handler = new IniciarCasoEventHandler(iniciarCasoUseCase);
    }

    private ContratoActivadoEvent eventoMinimo(String contratoId) {
        return new ContratoActivadoEvent(
            contratoId, "TIENDA-01",
            "Juan", "Pérez", "DNI", "12345678", "987654321", "juan@test.com",
            "Av. Lima 123", "Miraflores", "Lima", "Lima",
            "Ana", "García", "DNI", "87654321", "987111222", "ana@test.com", "CONYUGE",
            "Honda Wave 110cc 2023",
            3500.00, 3500.00,
            "2025-05-15",
            List.of(
                new ContratoActivadoEvent.CuotaActivadaDto(1, "2025-05-15", 175.00, "PENDIENTE"),
                new ContratoActivadoEvent.CuotaActivadaDto(2, "2025-06-15", 175.00, "PENDIENTE")
            ),
            "admin@motoya.pe"
        );
    }

    @Test
    void dryRunNoLlamaUseCase() {
        // autoIniciarEnabled = false (default)
        ContratoActivadoEvent event = eventoMinimo("CTR-9001");

        handler.handle(event);

        verifyNoInteractions(iniciarCasoUseCase);
    }

    @Test
    void modoRealLlamaUseCaseConCommandCorrecto() throws Exception {
        // Activar modo real vía reflexión (simula env var = true)
        var field = IniciarCasoEventHandler.class.getDeclaredField("autoIniciarEnabled");
        field.setAccessible(true);
        field.set(handler, true);

        CasoCobranzaDocument casoPersistido = new CasoCobranzaDocument();
        casoPersistido.setContratoId("CTR-9001");
        casoPersistido.setClienteNombre("Juan Pérez");
        when(iniciarCasoUseCase.ejecutar(any())).thenReturn(Mono.just(casoPersistido));

        ContratoActivadoEvent event = eventoMinimo("CTR-9001");
        handler.handle(event);

        // Dar tiempo al subscribe() async
        Thread.sleep(50);

        ArgumentCaptor<IniciarCasoCommand> captor = ArgumentCaptor.forClass(IniciarCasoCommand.class);
        verify(iniciarCasoUseCase, timeout(200)).ejecutar(captor.capture());

        IniciarCasoCommand cmd = captor.getValue();
        assertThat(cmd.contratoId()).isEqualTo("CTR-9001");
        assertThat(cmd.storeId()).isEqualTo("TIENDA-01");
        assertThat(cmd.motoDescripcion()).isEqualTo("Honda Wave 110cc 2023");
        assertThat(cmd.capitalOriginal()).isEqualTo(3500.00);
        assertThat(cmd.cronograma()).hasSize(2);
        assertThat(cmd.titular()).isNotNull();
        assertThat(cmd.titular().getNombres()).isEqualTo("Juan");
        assertThat(cmd.fiador()).isNotNull();
    }

    @Test
    void mapeoEstadoCuotaEsCorrecto() throws Exception {
        var field = IniciarCasoEventHandler.class.getDeclaredField("autoIniciarEnabled");
        field.setAccessible(true);
        field.set(handler, true);

        CasoCobranzaDocument caso = new CasoCobranzaDocument();
        when(iniciarCasoUseCase.ejecutar(any())).thenReturn(Mono.just(caso));

        ContratoActivadoEvent event = new ContratoActivadoEvent(
            "CTR-9002", "T1", "A", "B", "DNI", "11", "900", null,
            null, null, null, null, null, null, null, null, null, null, null,
            "Moto", 1000.0, 1000.0, null,
            List.of(
                new ContratoActivadoEvent.CuotaActivadaDto(1, "2025-01-01", 100.0, "PAGADO"),
                new ContratoActivadoEvent.CuotaActivadaDto(2, "2025-02-01", 100.0, "VENCIDO"),
                new ContratoActivadoEvent.CuotaActivadaDto(3, "2025-03-01", 100.0, "PENDIENTE")
            ), "SISTEMA"
        );
        handler.handle(event);
        Thread.sleep(50);

        ArgumentCaptor<IniciarCasoCommand> captor = ArgumentCaptor.forClass(IniciarCasoCommand.class);
        verify(iniciarCasoUseCase, timeout(200)).ejecutar(captor.capture());

        var cronograma = captor.getValue().cronograma();
        assertThat(cronograma.get(0).getEstado()).isEqualTo("PAGADA");
        assertThat(cronograma.get(1).getEstado()).isEqualTo("VENCIDA");
        assertThat(cronograma.get(2).getEstado()).isEqualTo("PENDIENTE");
    }
}
