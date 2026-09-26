package com.pedeai.printing.dto;

import com.pedeai.printing.domain.DocumentType;

import java.util.List;

/**
 * Documento pronto para imprimir, igual para a impressora térmica (ESC/POS, no agente) e para a impressão pelo
 * navegador. {@code columns}: caracteres por linha na fonte normal (32 no papel de 58mm, 48 no de 80mm).
 */
public record TicketResponse(DocumentType documentType, int columns, List<TicketLineResponse> lines) {
}
