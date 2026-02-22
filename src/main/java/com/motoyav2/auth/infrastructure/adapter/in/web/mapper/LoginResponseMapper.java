package com.motoyav2.auth.infrastructure.adapter.in.web.mapper;

import com.motoyav2.auth.domain.model.LoginResult;
import com.motoyav2.auth.domain.model.Store;
import com.motoyav2.auth.infrastructure.adapter.in.web.dto.LoginResponse;
import com.motoyav2.auth.infrastructure.adapter.in.web.dto.StoreInfoDto;

import java.util.List;

public final class LoginResponseMapper {

    private LoginResponseMapper() {
    }

    public static LoginResponse toResponse(LoginResult result) {
        List<StoreInfoDto> storeDtos = result.stores().stream()
                .map(LoginResponseMapper::toStoreDto)
                .toList();

        return new LoginResponse(
                result.tokenInfo().token(),
                result.tokenInfo().expiresIn(),
                result.user().uid(),
                result.user().firstName(),
                result.user().lastName(),
                result.user().email(),
                result.user().userType(),
                result.user().firstLogin(),
                storeDtos
        );
    }

    private static StoreInfoDto toStoreDto(Store store) {
        return new StoreInfoDto(
                store.id(),
                store.businessName(),
                store.address(),
                store.city()
        );
    }
}
