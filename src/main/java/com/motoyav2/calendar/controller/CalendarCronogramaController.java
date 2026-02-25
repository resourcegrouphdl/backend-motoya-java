package com.motoyav2.calendar.controller;

import com.motoyav2.calendar.dto.CronogramaRequest;
import com.motoyav2.calendar.dto.CronogramaResponse;
import com.motoyav2.calendar.service.CalendarCronogramaService;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controller provisional para integración con Google Calendar.
 *
 * Endpoint: POST /api/calendar/cronograma
 * Seguridad: requiere Google ID Token válido en Authorization: Bearer <token>
 *            El token es validado por FirebaseAuthenticationFilter antes de llegar aquí.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@ConditionalOnExpression("'${google.calendar.client-email:}' != ''")
public class CalendarCronogramaController {

    private final CalendarCronogramaService service;

    /**
     * Recibe un cronograma de cuotas y crea un evento todo-el-día en Google Calendar
     * por cada cuota. Los errores individuales no interrumpen el proceso.
     *
     * @param principal usuario autenticado (inyectado por Spring Security)
     * @param request   cronograma con lista de cuotas
     * @return resumen con total creados y errores detallados
     */
    @PostMapping("/cronograma")
    public Mono<CronogramaResponse> generarCronograma(
            @AuthenticationPrincipal FirebaseUserDetails principal,
            @RequestBody @Valid CronogramaRequest request) {

        log.info("[Calendar] POST /api/calendar/cronograma — uid={} cliente='{}'",
                principal.uid(), request.getNombreCliente());

        return service.generarCronograma(request);
    }
}
