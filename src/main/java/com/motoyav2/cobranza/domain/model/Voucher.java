package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.EstadoVoucher;
import com.motoyav2.cobranza.domain.enums.MotivoRechazoVoucher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Voucher {

    private String voucherId;
    private String contratoId;
    private String clienteId;
    private String agenteId;

    private String banco;
    private String numeroOperacion;
    private Double monto;
    private String fechaOperacion;

    private EstadoVoucher estado;
    private MotivoRechazoVoucher motivoRechazo;

    /** GCS path — Signed URL se genera on-demand, no se almacena */
    private String gcsPath;
    private OcrResultado ocrResultado;
    private String comprobanteId;

    private String createdAt;
    private String updatedAt;
}
