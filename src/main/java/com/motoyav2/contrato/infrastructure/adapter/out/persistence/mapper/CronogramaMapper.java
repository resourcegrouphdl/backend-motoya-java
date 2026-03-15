package com.motoyav2.contrato.infrastructure.adapter.out.persistence.mapper;

import com.motoyav2.calendar.dto.CronogramaRequest;
import com.motoyav2.calendar.dto.CuotaRequest;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.CuotaCronograma;

import java.time.ZoneId;
import java.util.List;

import static com.motoyav2.shared.utils.FormatMoney.PEN_FORMAT;

public class CronogramaMapper {
  public static CronogramaRequest toRequest(Contrato contrato) {

    String monto = PEN_FORMAT.format(contrato.datosFinancieros().cuotaMensual());
    String titular = contrato.titular().nombreCompleto().toUpperCase();
    String fiador = contrato.fiador().nombreCompleto().toUpperCase();

    CronogramaRequest request = new CronogramaRequest();

    request.setNombreCliente(titular + " " + monto);

    request.setDescripcion(
              titular + " "
            + contrato.titular().telefono() + "/"
            + fiador + " "
            + contrato.fiador().telefono()
    );

    request.setEstado("PENDIENTE");
    request.setContratoId(contrato.id());
    request.setStoreId(contrato.tienda().tiendaId());
    request.setCuotas(mapCuotas(contrato.cuotas()));
    request.setCreadoPor(contrato.creadoPor());

    // Datos completos del titular para que cobranzas cree el caso con info real
    var t = contrato.titular();
    request.setTitularNombres(t.nombres());
    request.setTitularApellidos(t.apellidos());
    request.setTitularTipoDocumento(t.tipoDocumento());
    request.setTitularNumeroDocumento(t.numeroDocumento());
    request.setTitularTelefono(t.telefono());
    request.setTitularEmail(t.email());
    request.setTitularDireccion(t.direccion());
    request.setTitularDistrito(t.distrito());
    request.setTitularProvincia(t.provincia());
    request.setTitularDepartamento(t.departamento());

    return request;
  }

  private static List<CuotaRequest> mapCuotas(List<CuotaCronograma> cuotas) {
    return cuotas.stream()
        .map(CronogramaMapper::mapCuota)
        .toList();
  }

  private static CuotaRequest mapCuota(CuotaCronograma cuota) {

    CuotaRequest r = new CuotaRequest();
    r.setNumero(cuota.numeroCuota());

    r.setFecha(
        cuota.fechaVencimiento()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    );

    return r;
  }
}
