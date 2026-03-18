package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.domain.model.Usuario;
import com.motoyav2.evaluacion.domain.port.out.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class UsuarioRepositoryAdapter implements UsuarioRepository {

    private static final String COL = "usuarios";
    private final Firestore db;

    @Override
    public Mono<Usuario> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(doc -> {
                    if (!doc.exists()) return null;
                    Map<String, Object> data = doc.getData();
                    if (data == null) return null;
                    String firstName = str(data, "firstName");
                    String lastName = str(data, "lastName");
                    String nombreCompleto = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                    return Usuario.builder()
                            .id(doc.getId())
                            .nombre(nombreCompleto.trim())
                            .email(str(data, "email"))
                            .rol(str(data, "userType"))
                            .build();
                });
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
