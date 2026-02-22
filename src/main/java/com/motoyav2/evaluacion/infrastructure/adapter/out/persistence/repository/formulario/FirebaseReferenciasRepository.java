package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseReferencias;
import org.springframework.stereotype.Repository;

@Repository
public interface FirebaseReferenciasRepository extends FirestoreReactiveRepository<FirebaseReferencias> {

}
