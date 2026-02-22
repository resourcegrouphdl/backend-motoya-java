package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.evaluacioncredito;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EvaluacionCreditoDocument;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluacionCreditoRepository extends FirestoreReactiveRepository<EvaluacionCreditoDocument> {

}
