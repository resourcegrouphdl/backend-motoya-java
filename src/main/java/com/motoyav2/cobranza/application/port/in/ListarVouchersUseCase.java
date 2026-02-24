package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
import reactor.core.publisher.Flux;

public interface ListarVouchersUseCase {

    Flux<VoucherDocument> ejecutar(String storeId, String estado);
}
