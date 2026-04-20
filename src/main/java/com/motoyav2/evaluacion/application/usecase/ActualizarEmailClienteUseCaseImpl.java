package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.model.ValidacionEmail;
import com.motoyav2.evaluacion.domain.port.in.ActualizarEmailClienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualizarEmailClienteUseCaseImpl implements ActualizarEmailClienteUseCase {

    private final ClienteRepository clienteRepository;

    @Override
    public Mono<ValidacionEmail> actualizarEmail(String clienteId, String nuevoEmail) {
        return checkEmail(nuevoEmail)
                .flatMap(resultado -> {
                    Map<String, Object> fields = Map.of(
                            "email", nuevoEmail != null ? nuevoEmail : "",
                            "validacionEmail", Map.of(
                                    "valido",       resultado.valido(),
                                    "nivel",        resultado.nivel() != null ? resultado.nivel() : "",
                                    "detalle",      resultado.detalle() != null ? resultado.detalle() : "",
                                    "verificadoEn", resultado.verificadoEn() != null
                                            ? resultado.verificadoEn() : Timestamp.now()
                            ),
                            "updatedAt", Timestamp.now()
                    );
                    return clienteRepository.updateFields(clienteId, fields)
                            .thenReturn(resultado);
                });
    }

    // ── MX check — igual que en IngresarSolicitudUseCaseImpl ──────────────

    private Mono<ValidacionEmail> checkEmail(String email) {
        if (email == null || email.isBlank()) {
            return Mono.just(build(false, "EMAIL_VACIO", "No se proporcionó email"));
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return Mono.just(build(false, "SINTAXIS_INVALIDA", "El email no tiene formato válido"));
        }
        String domain = email.substring(email.lastIndexOf('@') + 1);
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
                    return build(true, "MX_OK", "El dominio tiene servidores de correo configurados");
                } else {
                    return build(false, "DOMINIO_SIN_MX", "El dominio existe pero no tiene servidores de correo");
                }
            } catch (NamingException e) {
                return build(false, "DOMINIO_NO_ENCONTRADO",
                        "El dominio no existe o no se pudo resolver: " + domain);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ValidacionEmail build(boolean valido, String nivel, String detalle) {
        return new ValidacionEmail(valido, nivel, detalle, Timestamp.now());
    }
}
