package com.motoyav2.financiamiento;

import com.motoyav2.financiamiento.domain.model.CuotaCronograma;
import com.motoyav2.financiamiento.domain.model.ResultadoSimulacion;
import com.motoyav2.financiamiento.domain.model.SolicitudSimulacion;
import com.motoyav2.financiamiento.domain.service.MotorFinancieroService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MotorFinancieroService — amortización francesa con TEA")
class MotorFinancieroServiceTest {

    // ─── Caso base ────────────────────────────────────────────────────────────
    // precioVehiculo = 5000, cuotaInicial = 1000 (20%), 24 quincenas, TEA = 60%
    // montoFinanciado = 5000 + 890 - 1000 = 4890

    private static final BigDecimal PRECIO    = BigDecimal.valueOf(5000);
    private static final BigDecimal INICIAL   = BigDecimal.valueOf(1000);
    private static final BigDecimal TEA_60    = new BigDecimal("0.60");
    private static final int        N_24      = 24;

    private SolicitudSimulacion casoBase() {
        return SolicitudSimulacion.builder()
                .precioVehiculo(PRECIO)
                .cuotaInicial(INICIAL)
                .numeroCuotas(N_24)
                .tea(TEA_60)
                .build();
    }

    // ── 1. Monto financiado correcto ──────────────────────────────────────────

    @Test
    @DisplayName("montoFinanciado = precioVehiculo + gastos - cuotaInicial")
    void montoFinanciadoCorrecto() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        // 5000 + 890 - 1000 = 4890
        assertThat(r.getMontoFinanciado()).isEqualByComparingTo("4890.00");
    }

    // ── 2. Tasa quincenal derivada del TEA ────────────────────────────────────

    @Test
    @DisplayName("tasaQuincenal = (1 + 0.60)^(1/24) - 1  ≈ 0.01973")
    void tasaQuincenalCorrecta() {
        BigDecimal i = MotorFinancieroService.tasaQuincenal(TEA_60);
        // (1.60)^(1/24) - 1 ≈ 0.019726...
        assertThat(i.doubleValue()).isBetween(0.0196, 0.0199);
    }

    // ── 3. Cronograma con n cuotas exactas ───────────────────────────────────

    @Test
    @DisplayName("cronograma tiene exactamente numeroCuotas entradas")
    void cronogramaTamanio() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        assertThat(r.getCronograma()).hasSize(N_24);
    }

    // ── 4. Última cuota cierra el saldo en cero ───────────────────────────────

    @Test
    @DisplayName("saldoFinal de la última cuota es cero")
    void ultimaCuotaSaldoCero() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        CuotaCronograma ultima = r.getCronograma().get(N_24 - 1);
        assertThat(ultima.getSaldoFinal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── 5. Consistencia del cronograma ────────────────────────────────────────

    @Test
    @DisplayName("saldoFinal[k] == saldoInicial[k+1] para toda k")
    void encadenamientoDeSaldos() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        List<CuotaCronograma> c = r.getCronograma();
        for (int k = 0; k < c.size() - 1; k++) {
            assertThat(c.get(k).getSaldoFinal())
                    .as("saldo al final de cuota %d debe igual saldoInicial de cuota %d", k + 1, k + 2)
                    .isEqualByComparingTo(c.get(k + 1).getSaldoInicial());
        }
    }

    // ── 6. Relación cuota = interes + amortizacion ───────────────────────────

    @Test
    @DisplayName("cuota[k] == interes[k] + amortizacion[k] para cuotas normales")
    void cuotaIgualInteresMAsMortizacion() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        List<CuotaCronograma> c = r.getCronograma();
        // Verificamos todas menos la última (que puede variar por redondeo)
        for (int k = 0; k < c.size() - 1; k++) {
            BigDecimal suma = c.get(k).getInteres().add(c.get(k).getAmortizacion());
            assertThat(suma)
                    .as("cuota %d: interes + amortizacion debe igualar cuota", k + 1)
                    .isEqualByComparingTo(c.get(k).getCuota());
        }
    }

    // ── 7. totalIntereses == suma de intereses del cronograma ─────────────────

    @Test
    @DisplayName("totalIntereses coincide con suma de intereses del cronograma")
    void totalInteresesConsistente() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        BigDecimal sumaIntereses = r.getCronograma().stream()
                .map(CuotaCronograma::getInteres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(r.getTotalIntereses()).isEqualByComparingTo(sumaIntereses);
    }

    // ── 8. totalPagar == cuotaInicial + suma de cuotas ────────────────────────

    @Test
    @DisplayName("totalPagar == cuotaInicial + suma de todas las cuotas del cronograma")
    void totalPagarConsistente() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        BigDecimal sumaCuotas = r.getCronograma().stream()
                .map(CuotaCronograma::getCuota)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal esperado = r.getCuotaInicial().add(sumaCuotas);
        assertThat(r.getTotalPagar()).isEqualByComparingTo(esperado);
    }

    // ── 9. TCEA > TEA (gastos elevan el costo real) ───────────────────────────

    @Test
    @DisplayName("TCEA debe ser mayor que TEA cuando hay gastos administrativos")
    void tceaMayorQueTea() {
        ResultadoSimulacion r = MotorFinancieroService.simular(casoBase());
        double teaPct  = TEA_60.doubleValue() * 100;           // 60.0%
        double tceaPct = r.getTcea().doubleValue();
        assertThat(tceaPct).isGreaterThan(teaPct);
    }

    // ── 10. Plazos de excepción: 8, 10, 12, 14 ───────────────────────────────

    @Test
    @DisplayName("plazos de excepción generan cuotas más altas que 24 quincenas")
    void plazosExcepcionCuotasMasAltas() {
        ResultadoSimulacion base24 = MotorFinancieroService.simular(casoBase());

        for (int plazo : List.of(8, 10, 12, 14)) {
            ResultadoSimulacion excepcion = MotorFinancieroService.simular(
                    SolicitudSimulacion.builder()
                            .precioVehiculo(PRECIO)
                            .cuotaInicial(INICIAL)
                            .numeroCuotas(plazo)
                            .tea(TEA_60)
                            .build());

            assertThat(excepcion.getCuotaQuincenal())
                    .as("plazo %d debe tener cuota mayor que 24 quincenas", plazo)
                    .isGreaterThan(base24.getCuotaQuincenal());
        }
    }

    // ── 11. Simulación de opciones (múltiples plazos) ─────────────────────────

    @Test
    @DisplayName("simularOpciones devuelve una entrada por cada plazo")
    void simularOpciones() {
        List<Integer> plazos = List.of(16, 20, 24);
        List<ResultadoSimulacion> opciones = MotorFinancieroService.simularOpciones(
                PRECIO, INICIAL, TEA_60, plazos);

        assertThat(opciones).hasSize(3);
        assertThat(opciones.get(0).getNumeroCuotas()).isEqualTo(16);
        assertThat(opciones.get(1).getNumeroCuotas()).isEqualTo(20);
        assertThat(opciones.get(2).getNumeroCuotas()).isEqualTo(24);
    }

    // ── 12. Validación — cuota inicial mínima 20% ─────────────────────────────

    @Test
    @DisplayName("rechaza cuotaInicial menor al 20% del precio")
    void rechazaCuotaInicialInsuficiente() {
        SolicitudSimulacion solicitud = SolicitudSimulacion.builder()
                .precioVehiculo(BigDecimal.valueOf(5000))
                .cuotaInicial(BigDecimal.valueOf(500))   // 10% — muy poco
                .numeroCuotas(24)
                .tea(TEA_60)
                .build();

        assertThatThrownBy(() -> MotorFinancieroService.simular(solicitud))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20%");
    }

    // ── 13. Validación — TEA inválida ─────────────────────────────────────────

    @Test
    @DisplayName("rechaza TEA <= 0")
    void rechazaTeaInvalida() {
        SolicitudSimulacion solicitud = SolicitudSimulacion.builder()
                .precioVehiculo(PRECIO)
                .cuotaInicial(INICIAL)
                .numeroCuotas(24)
                .tea(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> MotorFinancieroService.simular(solicitud))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 14. Validación — precio cero ─────────────────────────────────────────

    @Test
    @DisplayName("rechaza precio del vehículo en cero o negativo")
    void rechazaPrecioCero() {
        SolicitudSimulacion solicitud = SolicitudSimulacion.builder()
                .precioVehiculo(BigDecimal.ZERO)
                .cuotaInicial(BigDecimal.ZERO)
                .numeroCuotas(24)
                .tea(TEA_60)
                .build();

        assertThatThrownBy(() -> MotorFinancieroService.simular(solicitud))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 15. Cuota menor con mayor inicial ────────────────────────────────────

    @Test
    @DisplayName("cuota disminuye cuando la inicial es mayor (mismo plazo y TEA)")
    void cuotaMenorConMayorInicial() {
        ResultadoSimulacion r1 = MotorFinancieroService.simular(SolicitudSimulacion.builder()
                .precioVehiculo(PRECIO).cuotaInicial(BigDecimal.valueOf(1000))
                .numeroCuotas(24).tea(TEA_60).build());

        ResultadoSimulacion r2 = MotorFinancieroService.simular(SolicitudSimulacion.builder()
                .precioVehiculo(PRECIO).cuotaInicial(BigDecimal.valueOf(2000))
                .numeroCuotas(24).tea(TEA_60).build());

        assertThat(r2.getCuotaQuincenal()).isLessThan(r1.getCuotaQuincenal());
    }

    // ── 16. Gastosadministrativos personalizados ──────────────────────────────

    @Test
    @DisplayName("gastos personalizados se reflejan en montoFinanciado")
    void gastosPersonalizados() {
        BigDecimal gastosCustom = BigDecimal.valueOf(500);
        ResultadoSimulacion r = MotorFinancieroService.simular(SolicitudSimulacion.builder()
                .precioVehiculo(PRECIO)
                .cuotaInicial(INICIAL)
                .numeroCuotas(24)
                .tea(TEA_60)
                .gastosAdministrativos(gastosCustom)
                .build());

        // montoFinanciado = 5000 + 500 - 1000 = 4500
        assertThat(r.getMontoFinanciado()).isEqualByComparingTo("4500.00");
        assertThat(r.getGastosAdministrativos()).isEqualByComparingTo("500");
    }
}
