package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseCliente;
import org.springframework.stereotype.Repository;

@Repository
public interface FirebaseClienteRepository extends FirestoreReactiveRepository<FirebaseCliente> {
}
