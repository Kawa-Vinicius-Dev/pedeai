package com.pedeai.shared.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Página de uma lista longa (histórico de pedidos, clientes). {@code page} começa em 0. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
