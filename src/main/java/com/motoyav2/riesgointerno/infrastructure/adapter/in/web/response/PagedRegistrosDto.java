package com.motoyav2.riesgointerno.infrastructure.adapter.in.web.response;

import lombok.Value;

import java.util.List;

@Value
public class PagedRegistrosDto {
    List<RegistroRiesgoDto> items;
    long total;
    int page;
    int size;
    int totalPages;

    public static PagedRegistrosDto from(List<RegistroRiesgoDto> items, long total, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PagedRegistrosDto(items, total, page, size, totalPages);
    }
}
