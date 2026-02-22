package com.motoyav2.auth.domain.port.out;

import com.motoyav2.auth.domain.model.Store;
import reactor.core.publisher.Flux;

import java.util.List;

public interface StoreRepository {

    Flux<Store> findByIds(List<String> ids);
}
