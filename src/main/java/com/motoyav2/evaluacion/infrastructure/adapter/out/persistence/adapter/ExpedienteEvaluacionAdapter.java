package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.ExpedientePort;
import com.motoyav2.evaluacion.domain.model.Documentos;
import com.motoyav2.evaluacion.domain.model.ExpedienteDeEvaluacion;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.evaluacioncredito.EvaluacionCreditoMapper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.evaluacioncredito.EvaluacionCreditoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpedienteEvaluacionAdapter implements ExpedientePort {

    private final EvaluacionCreditoRepository evaluacionCreditoRepository;
    private final EvaluacionCreditoMapper mapper;

    @Override
    public Mono<String> guardar(ExpedienteDeEvaluacion expediente) {
        List<Documentos> todosLosDocumentos = extraerDocumentos(expediente);

        var document = mapper.toFullDocument(
                expediente.getEvaluacion(),
                expediente.getTitular(),
                expediente.getFiador(),
                expediente.getVehiculo(),
                expediente.getFinanciamiento(),
                todosLosDocumentos,
                expediente.getReferencias(),
                null,
                null,
                null,
                null
        );

        return evaluacionCreditoRepository.save(document)
                .map(saved -> saved.getCodigoDeSolicitud());
    }

    private List<Documentos> extraerDocumentos(ExpedienteDeEvaluacion expediente) {
        List<Documentos> todos = new ArrayList<>();
        extraerDocumentosDePersona(expediente.getTitular(), "TITULAR", todos);
        if (expediente.getFiador() != null) {
            extraerDocumentosDePersona(expediente.getFiador(), "FIADOR", todos);
        }
        return todos;
    }

    private void extraerDocumentosDePersona(Persona persona, String tipoPersona, List<Documentos> destino) {
        if (persona == null || persona.getDocumentos() == null) return;
        for (Documentos doc : persona.getDocumentos()) {
            destino.add(Documentos.builder()
                    .id(doc.getId())
                    .tipoDocumento(doc.getTipoDocumento())
                    .tipoPersona(tipoPersona)
                    .nombre(doc.getNombre() != null ? doc.getNombre() : doc.getTipoDocumento())
                    .url(doc.getUrl())
                    .validado(doc.getValidado())
                    .observaciones(doc.getObservaciones())
                    .codigoDeSolicitud(doc.getCodigoDeSolicitud())
                    .evaluacionId(doc.getEvaluacionId())
                    .creadoEn(doc.getCreadoEn())
                    .actualizadoEn(doc.getActualizadoEn())
                    .build());
        }
    }
}
