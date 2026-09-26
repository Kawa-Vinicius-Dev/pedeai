package com.pedeai.printing.dto;

/**
 * Uma linha já quebrada na largura do papel. {@code big} é fonte dupla: ocupa o dobro da largura, por isso a linha
 * grande cabe em metade das colunas.
 */
public record TicketLineResponse(String text, Align align, boolean bold, boolean big) {
    public enum Align {
        LEFT, CENTER
    }
}
