package com.motoyav2.contrato.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.contrato.domain.port.out.ContratoNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.Year;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContratoNumberGeneratorAdapter implements ContratoNumberGenerator {

    private final Firestore firestore;

    @Override
    public Mono<String> generate(String evaluacionId) {
      return Mono.fromCallable(() -> generateInTransaction(evaluacionId))
          .subscribeOn(Schedulers.boundedElastic());
    }


  private String generateInTransaction(String evaluacionId) throws Exception {

    var counterRef = firestore.collection("counters").document("contratos");

    return firestore.runTransaction(transaction -> {

      var snapshot = transaction.get(counterRef).get();

      int year = Year.now().getValue();
      int month = LocalDate.now().getMonthValue();

      Long storedYear = snapshot.getLong("currentYear");
      Long storedSequence = snapshot.getLong("currentSequence");

      long sequence;

      if (storedYear == null || storedYear != year) {
        sequence = 1;
      } else {
        sequence = (storedSequence != null ? storedSequence : 0) + 1;
      }

      transaction.set(counterRef, Map.of(
          "currentYear", year,
          "currentSequence", sequence
      ));

      // Limpiamos evaluacionId (opcional)
      String evalShort = evaluacionId
          .replaceAll("[^a-zA-Z0-9]", "")
          .toUpperCase();

      if (evalShort.length() > 6) {
        evalShort = evalShort.substring(0, 6);
      }

      return String.format(
          "MTD-%d-%02d-%s-%06d",
          year,
          month,
          evalShort,
          sequence
      );

    }).get();
  }
}
